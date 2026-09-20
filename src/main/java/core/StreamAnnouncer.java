package core;

import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.GameList;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec;
import discord4j.rest.util.Color;

import models.GuildStructure;
import models.ChannelDels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamAnnouncer {
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);   // announcer based on executor
    private GuildStructure g;
    private Semaphore streamSemaphore = new Semaphore(1);   // to avoid adding/removing games/channels during announcement
    private Logger announcerLogger = LoggerFactory.getLogger(StreamAnnouncer.class);
    private final String twitchIcon = "https://abload.de/img/twitchicono8dj7.png";  // twitchicon for the embedded messages
    private Lembot lembot;
    private DBHandler dbHandler;
    private Boolean setGameFilters;


    public StreamAnnouncer(GuildStructure g) {
        this.g = g;
        this.lembot = g.getLembot();
        dbHandler = lembot.getDbHandler();

        scheduler.scheduleAtFixedRate(this::announceStreams, 1, 1, TimeUnit.MINUTES);
    }

    private void announceStreams() {

        try {
            streamSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        announcerLogger.info("Announcer for guild {} working", g.getGuild_id());

        if (g.getAnnounce_channel() != null) {
            TextChannel announce_channel = (TextChannel) lembot.getGatewayDiscordClient()
                    .getChannelById(Snowflake.of(g.getAnnounce_channel()))
                    .block();

            List<ChannelDels> channelDels = g.getTwitch_channels();
            List<String> gameIDs = new ArrayList<>(g.getGame_filters().keySet());
            setGameFilters = !gameIDs.isEmpty();

            Map<Long, ChannelDels> channels = new HashMap<>();
            Map<Long, Long> unfilteredGameStreams = new HashMap<>();

            List<String> unfilteredGameIDs = new ArrayList<>();

            for (ChannelDels cd : channelDels) {
                // crappy code section due to weird errors coming from Twitch4J's API implementation
                channels.put(cd.getChannelID(), cd);
            }

            // Check for changes in Channel, i. e. new icon or name
            List<Long> allChannels = new ArrayList<>(channels.keySet());

            if (!allChannels.isEmpty()) {
                try {
                    try {
                        UserList resultUserList = lembot.getUsers(allChannels, null);
                        List<User> userList = resultUserList.getUsers();

                        for (User u : userList) {
                            allChannels.remove(u.getId());
                            ChannelDels cd = channels.get(u.getId());
                            String curName = u.getDisplayName();
                            if (!cd.getName().equals(curName)) {
                                cd.setName(u.getDisplayName());
                                dbHandler.updateName(g.getGuild_id(), cd.getChannelID(), cd.getName());
                            }
                            cd.setIconUrl(u.getProfileImageUrl());
                            cd.setRemove_flag(0);
                        }
                    } catch (Exception e) {
                        announcerLogger.error("Error occured at user check for guild {}.", g.getGuild_id(), e);
                        throw e;
                    }

                    for (Long l : allChannels) {
                        ChannelDels cd = channels.get(l);
                        Integer removeFlag = cd.getRemove_flag();
                        if (removeFlag > 2) {
                            lembot.sendMessage(announce_channel, "Channel " + cd.getName() + " (id: " + cd.getChannelID() + ") might have been deleted. It will be removed.");
                            g.removeChannel(cd);
                            dbHandler.deleteChannelForGuild(
                                    lembot.getGatewayDiscordClient()
                                            .getGuildById(Snowflake.of(g.getGuild_id()))
                                            .block(),
                                    cd.getChannelID()
                            );
                        } else {
                            cd.setRemove_flag(++removeFlag);
                        }
                        channels.remove(l);
                    }

                    try {
                        StreamList resultList = lembot.getStreams(gameIDs, new ArrayList<Long>(channels.keySet()));
                        List<Stream> streams = resultList.getStreams();

                        for (Stream s : streams) {
                            Long streamGameId = Long.valueOf(s.getGameId());
                            ChannelDels cd = channels.get(s.getUserId());
                            // was live and still streaming filtered game
                            if (cd.getLive()) {
                                if (!streamGameId.equals(cd.getGameID())) {        // streams another filtered game
                                    cd.setGameID(streamGameId);
                                    cd.setTitle(s.getTitle());
                                    cd.setOffline_flag(0);

                                    if (setGameFilters) {
                                        String newGame = g.getGame_filters().get(String.valueOf(streamGameId));

                                        cd.setGame(newGame);


                                        if (g.getMessage_style().equals(0)) {
                                            editClassicMessage(announce_channel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                        } else {
                                            editEmbedMessage(announce_channel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                        }

                                        dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                    }
                                    else {
                                        System.out.println(streamGameId);

                                        unfilteredGameIDs.add(String.valueOf(streamGameId));
                                        unfilteredGameStreams.put(cd.getChannelID(), streamGameId);
                                    }

                                } else if (!s.getTitle().equals(cd.getTitle())) {    // changed title
                                    cd.setTitle(s.getTitle());
                                    cd.setOffline_flag(0);

                                    if (g.getMessage_style().equals(0)) {
                                        editClassicMessage(announce_channel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                    } else {
                                        editEmbedMessage(announce_channel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                    }

                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                }
                                channels.remove(s.getUserId());
                            } else {      // was offline and went live
                                if (setGameFilters) {
                                    cd.setGame(g.getGame_filters().get(String.valueOf(streamGameId)));
                                    cd.setGameID(streamGameId);
                                    cd.setTitle(s.getTitle());
                                    cd.setOffline_flag(0);
                                    cd.setLive(true);

                                    if (g.getMessage_style().equals(0)) {
                                        cd.setPostID(sendClassicMessage(announce_channel, cd.getName(), cd.getGame(), cd.getTitle()));
                                    } else {
                                        cd.setPostID(sendEmbedMessage(announce_channel, cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl()));
                                    }
                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                    channels.remove(s.getUserId());
                                } else {
                                    cd.setTitle(s.getTitle());
                                    cd.setGameID(streamGameId);

                                    unfilteredGameIDs.add(String.valueOf(streamGameId));
                                    unfilteredGameStreams.put(cd.getChannelID(), streamGameId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        announcerLogger.error("Error occured in Stream check for guild {}.", g.getGuild_id(), e);
                        throw e;
                    }

                    if (!setGameFilters && !unfilteredGameIDs.isEmpty()) {
                        try {
                            GameList resultGameList = lembot.getGames(unfilteredGameIDs, null);
                            List<Game> gameList = resultGameList.getGames();

                            Map<String, String> games = new HashMap<>();
                            for (Game g : gameList) {
                                games.put(g.getId(), g.getName());
                            }

                            for (Long l : unfilteredGameStreams.keySet()) {
                                ChannelDels cd = channels.get(l);

                                cd.setGame(games.get(String.valueOf(unfilteredGameStreams.get(cd.getChannelID()))));  // TODO: unfilteredGameStreams sometimes NULL
                                cd.setOffline_flag(0);
                                cd.setLive(true);

                                if (g.getMessage_style().equals(0)) {
                                    cd.setPostID(sendClassicMessage(announce_channel, cd.getName(), cd.getGame(), cd.getTitle()));
                                } else {
                                    cd.setPostID(sendEmbedMessage(announce_channel, cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl()));
                                }

                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                channels.remove(l);
                            }
                        } catch (Exception e) {
                            announcerLogger.error("Error occured in Game check for guild {}.", g.getGuild_id(), e);
                            throw e;
                        }

                    }

                    for (ChannelDels cd : channels.values()) {
                        // was live and now offline (or streams not filtered game)
                        if (cd.getLive()) {
                            if (cd.getOffline_flag() < 3) { // 3 minutes chance to reconnect
                                Integer offline_flag = cd.getOffline_flag() + 1;
                                cd.setOffline_flag(offline_flag);
                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), offline_flag);
                            } else {  // more than 3 minutes offline
                                if (!g.getCleanup()) {       // no cleanup -> offline messages
                                    if (g.getMessage_style().equals(0)) {
                                        editClassicOffMessage(announce_channel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                    } else {
                                        editEmbedOffMessage(announce_channel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                    }
                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 0, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                } else {
                                    lembot.deleteMessage(announce_channel, cd.getPostID());
                                    cd.setPostID(null);
                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 0, 0L, cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                }
                                cd.setLive(false);
                                cd.setOffline_flag(0);
                            }
                        }
                    }
                } catch (Exception e) {
                    announcerLogger.error("Due to error iteration of announcer was skipped in guild {}", g.getGuild_id(), e);
                }
            }

            streamSemaphore.release();
            announcerLogger.info("Announcer for guild {} was successful", g.getGuild_id());

        }
        else {
            announcerLogger.info("Announcer for guild {} was not successful because an announcement channel was not set up", g.getGuild_id());
        }
    }

    public void shutdownScheduler() {
        scheduler.shutdownNow();
        announcerLogger.warn("Announcer of guild {} was shutdown", g.getGuild_id());
        streamSemaphore = new Semaphore(1);
    }

    public void restartScheduler() {
        if (!scheduler.isShutdown()) {
            shutdownScheduler();
        }

        scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(this::announceStreams,1,1, TimeUnit.MINUTES);

        announcerLogger.warn("Announcer of guild {} was restarted", g.getGuild_id());
    }

    public Semaphore getStreamSemaphore() {
        return streamSemaphore;
    }

    private Consumer<LegacyEmbedCreateSpec> buildEmbedMessage(String channelName, String game, String title, String iconUrl) {
        return spec -> {
            if (game != null) {
                spec.addField("Playing", game, true);
            }

            if (title != null) {
                spec.addField("Title", title, false);
            }

            String twitchUrl = "https://twitch.tv/" + channelName;

            spec.setAuthor(channelName + " has gone live!", twitchUrl, twitchIcon);

            spec.setColor(Color.of(100, 65, 164));
            spec.setTitle(twitchUrl);
            spec.setUrl(twitchUrl);
            spec.setThumbnail(iconUrl);
        };
    }

    private Consumer<LegacyEmbedCreateSpec> buildEmbedOffMessage(String channelName, String game, String title, String iconUrl) {
        return spec -> {
            if (game != null) {
                spec.addField("Game", game, true);
            }

            if (title != null) {
                spec.addField("Title", title, false);
            }

            String twitchUrl = "https://twitch.tv/" + channelName;

            spec.setAuthor("[OFFLINE]: " + channelName + " was streaming", null, twitchIcon);

            spec.setTitle(twitchUrl);
            spec.setUrl(twitchUrl);
        };
    }

    private String buildClassicMessage(String channelName, String game, String title) {
        String message = channelName + " has gone live";

        if (game != null) {
            message += ", streaming " + game;
        }

        if (title != null) {
            message += ": **" + title + "**";
        }
        message += "\nhttps://twitch.tv/" + channelName;

        return message;
    }

    private String buildClassicOffMessage(String channelName, String game, String title) {
        String message = "[OFFLINE]: " + channelName + " was streaming";

        if (game != null) {
            message += " " + game;
        }

        if (title != null) {
            message += ": **" + title + "**";
        }
        message += "\n<https://twitch.tv/" + channelName + ">";

        return message;
    }

    private Long sendClassicMessage(Channel channel, String channelName, String game, String title) {
        return lembot.sendMessageID(channel, buildClassicMessage(channelName, game, title));
    }

    private void editClassicMessage(Channel channel, Long postID, String channelName, String game, String title) {
        lembot.editMessage(channel, postID, buildClassicMessage(channelName, game, title));
    }

    private void editClassicOffMessage(Channel channel, Long postID, String channelName, String game, String title) {
        lembot.editMessage(channel, postID, buildClassicOffMessage(channelName, game, title));
    }

    private Long sendEmbedMessage(Channel channel, String channelName, String game, String title, String iconUrl) {
        return lembot.sendMessageID(channel, buildEmbedMessage(channelName, game, title, iconUrl));
    }

    private void editEmbedMessage(Channel channel, Long postID, String channelName, String game, String title, String iconUrl) {
        lembot.editMessage(channel, postID, buildEmbedMessage(channelName, game, title, iconUrl));
    }

    private void editEmbedOffMessage(Channel channel, Long postID, String channelName, String game, String title, String iconUrl) {
        lembot.editMessage(channel, postID, buildEmbedOffMessage(channelName, game, title, iconUrl));
    }
}

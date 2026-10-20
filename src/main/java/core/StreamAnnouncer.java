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

    // private final String twitchIcon = "https://abload.de/img/twitchicono8dj7.png";
    private static final String twitchIcon = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/twitch.png";
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private GuildStructure g;
    private Semaphore streamSemaphore = new Semaphore(1);
    private Logger announcerLogger = LoggerFactory.getLogger(StreamAnnouncer.class);
    private Lembot lembot;
    private DBHandler dbHandler;
    private Boolean setGameFilters;


    public StreamAnnouncer(GuildStructure g) {
        this.g = g;
        this.lembot = g.getLembot();
        this.dbHandler = lembot.getDbHandler();

        scheduler.scheduleAtFixedRate(this::announceStreams, 1, 1, TimeUnit.MINUTES);
    }


    private void announceStreams() {

        boolean acquired = false;

        try {
            streamSemaphore.acquire();
            acquired = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            announcerLogger.warn("Announcer for guild {} was interrupted before starting.", g.getGuild_id());
            return;
        }

        try {

            announcerLogger.info("Announcer for guild {} working", g.getGuild_id());

            if (g.getAnnounce_channel() == null) {
                announcerLogger.info("Announcer for guild {} was not successful because an announcement channel was not set up", g.getGuild_id());
                return;
            }

            TextChannel announceChannel = (TextChannel) lembot.getGatewayDiscordClient().getChannelById(Snowflake.of(g.getAnnounce_channel())).block();

            if (announceChannel == null) {
                announcerLogger.warn("Announcement channel {} for guild {} could not be found.", g.getAnnounce_channel(), g.getGuild_id());
                return;
            }

            List<ChannelDels> channelDels = g.getTwitch_channels();
            List<String> gameIDs = new ArrayList<>(g.getGame_filters().keySet());
            setGameFilters = !gameIDs.isEmpty();

            Map<Long, ChannelDels> channels = new HashMap<>();
            Map<Long, Long> unfilteredGameStreams = new HashMap<>();
            List<String> unfilteredGameIDs = new ArrayList<>();

            for (ChannelDels cd : channelDels) {
                if (cd == null || cd.getChannelID() == null) {
                    announcerLogger.warn("Ignoring invalid ChannelDels entry in guild {}.", g.getGuild_id());
                    continue;
                }
                channels.put(cd.getChannelID(), cd);
            }

            /*
             * Check for changes in channels:
             * - name
             * - profile image
             * - deleted channels
             */
            List<Long> allChannels = new ArrayList<>(channels.keySet());

            if (!allChannels.isEmpty()) {

                try {
                    UserList resultUserList = lembot.getUsers(allChannels, null);
                    List<User> userList = resultUserList.getUsers();

                    for (User u : userList) {
                        if (u == null || u.getId() == null) {
                            continue;
                        }

                        String userIdString = u.getId();
                        Long userId;
                        try {
                            userId = Long.valueOf(userIdString);
                        } catch (NumberFormatException e) {
                            announcerLogger.warn("Received invalid Twitch user ID '{}' for guild {}.", userIdString, g.getGuild_id());
                            continue;
                        }

                        allChannels.remove(userId);
                        ChannelDels cd = channels.get(userId);

                        if (cd == null) {
                            announcerLogger.warn("Twitch returned user {} (ID: {}), but no matching ChannelDels entry exists for guild {}.", u.getDisplayName(), userId, g.getGuild_id());
                            continue;
                        }

                        String curName = u.getDisplayName();
                        if (curName != null && !curName.equals(cd.getName())) {
                            cd.setName(curName);
                            dbHandler.updateName(g.getGuild_id(), cd.getChannelID(), cd.getName());
                        }

                        cd.setIconUrl(u.getProfileImageUrl());
                        cd.setRemove_flag(0);
                    }

                } catch (Exception e) {
                    announcerLogger.error("Error occured at user check for guild {}.", g.getGuild_id(), e);
                    throw e;
                }

                for (Long channelId : allChannels) {
                    ChannelDels cd = channels.get(channelId);
                    if (cd == null) {
                        continue;
                    }

                    Integer removeFlag = cd.getRemove_flag();
                    if (removeFlag == null) {
                        removeFlag = 0;
                    }

                    if (removeFlag > 2) {
                        lembot.sendMessage(announceChannel, "Channel " + cd.getName() + " (id: " + cd.getChannelID() + ") might have been deleted. It will be removed.");
                        g.removeChannel(cd);
                        dbHandler.deleteChannelForGuild(lembot.getGatewayDiscordClient().getGuildById(Snowflake.of(g.getGuild_id())).block(), cd.getChannelID());
                    } else {
                        cd.setRemove_flag(removeFlag + 1);
                    }

                    channels.remove(channelId);
                }

                /*
                 * Check currently active streams.
                 */
                try {
                    StreamList resultList = lembot.getStreams(gameIDs, new ArrayList<>(channels.keySet()));
                    List<Stream> streams = resultList.getStreams();

                    for (Stream s : streams) {
                        if (s == null || s.getUserId() == null) {
                            continue;
                        }

                        Long userId;
                        try {
                            userId = Long.valueOf(s.getUserId());
                        } catch (NumberFormatException e) {
                            announcerLogger.warn("Received invalid Twitch stream user ID '{}' for guild {}.", s.getUserId(), g.getGuild_id());
                            continue;
                        }

                        ChannelDels cd = channels.get(userId);
                        if (cd == null) {
                            announcerLogger.warn("Received stream for Twitch user ID {}, but no matching ChannelDels was found in guild {}.", userId, g.getGuild_id());
                            continue;
                        }

                        Long streamGameId = null;
                        if (s.getGameId() != null && !s.getGameId().isBlank()) {
                            try {
                                streamGameId = Long.valueOf(s.getGameId());
                            } catch (NumberFormatException e) {
                                announcerLogger.warn("Received invalid Twitch game ID '{}' for channel {}.", s.getGameId(), cd.getName());
                            }
                        }

                        boolean isLive = Boolean.TRUE.equals(cd.getLive());
                        boolean titleChanged = s.getTitle() != null && !s.getTitle().equals(cd.getTitle());
                        boolean gameChanged = (streamGameId != null && !streamGameId.equals(cd.getGameID()))
                                || (streamGameId == null && cd.getGameID() != null);

                        /*
                         * Channel was already live.
                         */
                        if (isLive) {
                            cd.setOffline_flag(0);

                            if (setGameFilters) {
                                if (gameChanged || titleChanged) {
                                    if (streamGameId != null) {
                                        cd.setGame(g.getGame_filters().get(String.valueOf(streamGameId)));
                                        cd.setGameID(streamGameId);
                                    } else {
                                        cd.setGame(null);
                                        cd.setGameID(null);
                                    }

                                    if (s.getTitle() != null) {
                                        cd.setTitle(s.getTitle());
                                    }

                                    if (cd.getPostID() != null && cd.getPostID() != 0) {
                                        if (g.getMessage_style().equals(0)) {
                                            editClassicMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                        } else {
                                            editEmbedMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                        }
                                    }

                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                }
                                channels.remove(userId);

                            } else {
                                // NO GAME FILTERS CONFIGURED
                                if (gameChanged) {
                                    if (s.getTitle() != null) {
                                        cd.setTitle(s.getTitle());
                                    }

                                    if (streamGameId != null) {
                                        unfilteredGameIDs.add(String.valueOf(streamGameId));
                                        unfilteredGameStreams.put(cd.getChannelID(), streamGameId);
                                        // Kept in channels so game resolution below can find it!
                                    } else {
                                        // Category was cleared (streaming without a game)
                                        cd.setGame(null);
                                        cd.setGameID(null);

                                        if (cd.getPostID() != null && cd.getPostID() != 0) {
                                            if (g.getMessage_style().equals(0)) {
                                                editClassicMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                            } else {
                                                editEmbedMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                            }
                                        }

                                        dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                        channels.remove(userId);
                                    }

                                } else if (titleChanged) {
                                    // Game didn't change, only title changed
                                    cd.setTitle(s.getTitle());

                                    if (cd.getPostID() != null && cd.getPostID() != 0) {
                                        if (g.getMessage_style().equals(0)) {
                                            editClassicMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                        } else {
                                            editEmbedMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                        }
                                    }

                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                    channels.remove(userId);

                                } else {
                                    // Live and unchanged
                                    channels.remove(userId);
                                }
                            }

                        } else {

                            /*
                             * Channel was offline and went live.
                             */
                            if (setGameFilters) {
                                cd.setGame(streamGameId == null ? null : g.getGame_filters().get(String.valueOf(streamGameId)));
                                cd.setGameID(streamGameId);
                                cd.setTitle(s.getTitle());
                                cd.setOffline_flag(0);
                                cd.setLive(true);

                                if (g.getMessage_style().equals(0)) {
                                    cd.setPostID(sendClassicMessage(announceChannel, cd.getName(), cd.getGame(), cd.getTitle()));
                                } else {
                                    cd.setPostID(sendEmbedMessage(announceChannel, cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl()));
                                }

                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                channels.remove(userId);

                            } else {
                                cd.setTitle(s.getTitle());
                                cd.setGameID(streamGameId);

                                if (streamGameId != null) {
                                    unfilteredGameIDs.add(String.valueOf(streamGameId));
                                    unfilteredGameStreams.put(cd.getChannelID(), streamGameId);
                                } else {
                                    cd.setGame(null);
                                    cd.setOffline_flag(0);
                                    cd.setLive(true);

                                    if (g.getMessage_style().equals(0)) {
                                        cd.setPostID(sendClassicMessage(announceChannel, cd.getName(), cd.getGame(), cd.getTitle()));
                                    } else {
                                        cd.setPostID(sendEmbedMessage(announceChannel, cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl()));
                                    }

                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                                    channels.remove(userId);
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    announcerLogger.error("Error occured in Stream check for guild {}.", g.getGuild_id(), e);
                    throw e;
                }

                /*
                 * No game filters:
                 * Resolve game IDs into game names and post/edit messages.
                 */
                if (!setGameFilters && !unfilteredGameIDs.isEmpty()) {

                    try {
                        GameList resultGameList = lembot.getGames(unfilteredGameIDs, null);
                        List<Game> gameList = resultGameList.getGames();

                        Map<String, String> games = new HashMap<>();
                        for (Game game : gameList) {
                            games.put(game.getId(), game.getName());
                        }

                        for (Map.Entry<Long, Long> entry : unfilteredGameStreams.entrySet()) {
                            Long channelId = entry.getKey();
                            Long gameId = entry.getValue();
                            ChannelDels cd = channels.get(channelId);

                            if (cd == null) {
                                announcerLogger.warn("Could not find ChannelDels for channel ID {} while resolving game information in guild {}.", channelId, g.getGuild_id());
                                continue;
                            }

                            cd.setGame(games.get(String.valueOf(gameId)));
                            cd.setGameID(gameId); // Save new game ID in memory
                            cd.setOffline_flag(0);

                            if (Boolean.TRUE.equals(cd.getLive())) {
                                // CHANNEL WAS ALREADY LIVE -> EDIT EXISTING MESSAGE
                                if (cd.getPostID() != null && cd.getPostID() != 0) {
                                    if (g.getMessage_style().equals(0)) {
                                        editClassicMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                    } else {
                                        editEmbedMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                    }
                                }
                            } else {
                                // CHANNEL JUST WENT LIVE -> CREATE NEW MESSAGE
                                cd.setLive(true);
                                if (g.getMessage_style().equals(0)) {
                                    cd.setPostID(sendClassicMessage(announceChannel, cd.getName(), cd.getGame(), cd.getTitle()));
                                } else {
                                    cd.setPostID(sendEmbedMessage(announceChannel, cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl()));
                                }
                            }

                            dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                            channels.remove(channelId);
                        }

                    } catch (Exception e) {
                        announcerLogger.error("Error occured in Game check for guild {}.", g.getGuild_id(), e);
                        throw e;
                    }
                }

                /*
                 * Remaining channels did not appear in the stream list.
                 */
                for (ChannelDels cd : channels.values()) {
                    if (cd == null) {
                        continue;
                    }

                    if (Boolean.TRUE.equals(cd.getLive())) {
                        Integer offlineFlag = cd.getOffline_flag();
                        if (offlineFlag == null) {
                            offlineFlag = 0;
                        }

                        if (offlineFlag < 3) {
                            offlineFlag++;
                            cd.setOffline_flag(offlineFlag);
                            dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 1, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), offlineFlag);

                        } else {
                            if (!g.getCleanup()) {
                                if (g.getMessage_style().equals(0)) {
                                    editClassicOffMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle());
                                } else {
                                    editEmbedOffMessage(announceChannel, cd.getPostID(), cd.getName(), cd.getGame(), cd.getTitle(), cd.getIconUrl());
                                }
                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 0, cd.getPostID(), cd.getTitle(), cd.getGame(), cd.getGameID(), 0);

                            } else {
                                if (cd.getPostID() != null && cd.getPostID() != 0) {
                                    lembot.deleteMessage(announceChannel, cd.getPostID());
                                }
                                cd.setPostID(null);
                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), cd.getName(), 0, 0L, cd.getTitle(), cd.getGame(), cd.getGameID(), 0);
                            }

                            cd.setLive(false);
                            cd.setOffline_flag(0);
                        }
                    }
                }
            }

        } catch (Exception e) {
            announcerLogger.error("Due to error iteration of announcer was skipped in guild {}", g.getGuild_id(), e);

        } finally {
            if (acquired) {
                streamSemaphore.release();
            }
            announcerLogger.info("Announcer for guild {} was successful", g.getGuild_id());
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

        scheduler.scheduleAtFixedRate(this::announceStreams, 1, 1, TimeUnit.MINUTES);

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

            if (iconUrl != null) {
                spec.setThumbnail(iconUrl);
            }
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
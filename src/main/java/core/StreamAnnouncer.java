package core;

import me.philippheuer.twitch4j.exceptions.ChannelDoesNotExistException;
import models.GuildStructure;
import models.ChannelDels;

import java.util.List;
import java.util.concurrent.*;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import me.philippheuer.twitch4j.endpoints.StreamEndpoint;
import me.philippheuer.twitch4j.model.Channel;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.util.EmbedBuilder;

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

        IChannel announce_channel = lembot.getDiscordClient().getChannelByID(g.getAnnounce_channel());

        List<ChannelDels> channelDels = g.getTwitch_channels();
        List<String> games = g.getGame_filters();

        ChannelEndpoint channelEndpoint = lembot.getChannelEndpoint();
        StreamEndpoint streamEndpoint = lembot.getStreamEndpoint();
        Channel c = null;

        for (ChannelDels cd : channelDels) {
            // crappy code section due to weird errors coming from Twitch4J's API implementation
            int retries = 3;     // 3 retries for Channel detection

            while (true) {
                try {
                    c = channelEndpoint.getChannel(cd.getChannelID());
                    break;
                } catch (ChannelDoesNotExistException e) {
                    announcerLogger.error("Channel {} with id: {} returned null in guild {}", cd.getName(), cd.getChannelID(), g.getGuild_id(), e);
                    if (--retries < 1) {
                        Integer removeFlag = cd.getRemove_flag();
                        if (removeFlag > 2) {
                            lembot.sendMessage(announce_channel, "Channel {} (id: {}) might have been deleted. It will be removed.");
                            g.removeChannel(cd);
                            dbHandler.deleteChannelForGuild(lembot.getDiscordClient().getGuildByID(g.getGuild_id()), cd.getChannelID());
                        }
                        else {
                            cd.setRemove_flag(++removeFlag);
                        }
                        cd.setRemove_flag(cd.getRemove_flag() + 1);
                        c = null;
                        break;
                    }
                } catch (Exception e) {
                    announcerLogger.error("Channel {} with id: {} returned null in guild {}", cd.getName(), cd.getChannelID(), g.getGuild_id(), e);
                    break;
                }
            }

            if (c == null) {
                continue;
            }

            cd.setRemove_flag(0);

            // update channel name if changed
            if (!c.getName().equals(cd.getName())) {
                cd.setName(c.getName());
                dbHandler.updateName(g.getGuild_id(), cd.getChannelID(), c.getName());
            }

            // Channel was offline and goes live
            if (!cd.getLive()) {
                try {
                    if (streamEndpoint.isLive(c)) {
                        if (games.isEmpty() || games.contains(toLowerCase(c.getGame()))) {
                            Long id;
                            if (g.getMessage_style().equals(0)) {
                                id = sendClassicMessage(announce_channel, c.getName(), c.getGame(), c.getStatus());
                            }
                            else {
                                id = sendEmbedMessage(announce_channel, c.getName(), c.getGame(), c.getStatus(), c.getLogo());
                            }
                            // if message could not be posted (missing permissions) then it will try again on the next cycle
                            if (id != null) {
                                // Write to DB in case bot has to go offline and save to local variables
                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 1, id, c.getStatus(), c.getGame(), 0);

                                cd.setLive(true);
                                cd.setPostID(id);
                                cd.setTitle(c.getStatus());
                                cd.setGame(c.getGame());
                                cd.setOffline_flag(0);
                            }
                        }
                    }
                } catch (Exception e) {
                    announcerLogger.error("Problem with announcer: guild {} and channel {}", g.getGuild_id(), c.getId(), e);
                }
            }
            // Channel is or was already live
            else {
                try {
                    if (!streamEndpoint.isLive(c)) {    // channel has gone offline
                        if (cd.getOffline_flag() < 3) {      // 3 minute chance to go back live
                            Integer offline_flag = cd.getOffline_flag() + 1;
                            cd.setOffline_flag(offline_flag);
                            dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 1, cd.getPostID(), c.getStatus(), c.getGame(), offline_flag);
                        }
                        else {
                            if(!g.getCleanup()) {       // no cleanup -> offline messages
                                if (g.getMessage_style().equals(0)) {
                                    editClassicOffMessage(announce_channel, cd.getPostID(), c.getName(), cd.getGame(), cd.getTitle());
                                }
                                else {
                                    editEmbedOffMessage(announce_channel, cd.getPostID(), c.getName(), cd.getGame(), cd.getTitle(), c.getLogo());
                                }
                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 0, cd.getPostID(), c.getStatus(), c.getGame(), 0);
                            }
                            else {
                                lembot.deleteMessage(announce_channel, cd.getPostID());
                                cd.setPostID(null);
                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 0, 0L, c.getStatus(), c.getGame(), 0);
                            }
                            cd.setLive(false);
                            cd.setOffline_flag(0);
                        }
                    }
                    else {      // channel is still live
                        if (!cd.getGame().equals(c.getGame())) {
                            if (games.isEmpty() || games.contains(toLowerCase(c.getGame()))) {      // Streamed followed game and changes to followed game
                                if (g.getMessage_style().equals(0)) {
                                    editClassicMessage(announce_channel, cd.getPostID(), c.getName(), c.getGame(), c.getStatus());
                                }
                                else {
                                    editEmbedMessage(announce_channel, cd.getPostID(), c.getName(), c.getGame(), c.getStatus(), c.getLogo());
                                }

                                dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 1, cd.getPostID(), c.getStatus(), c.getGame(), 0);
                                cd.setGame(c.getGame());
                                cd.setTitle(c.getStatus());
                                cd.setOffline_flag(0);
                            }
                            else {              // Streamed followed game and changes to not followed game
                                if (!g.getCleanup()) {
                                    if (g.getMessage_style().equals(0)) {
                                        editClassicOffMessage(announce_channel, cd.getPostID(), c.getName(), cd.getGame(), cd.getTitle());
                                    }
                                    else {
                                        editEmbedOffMessage(announce_channel, cd.getPostID(), c.getName(), cd.getGame(), cd.getTitle(), c.getLogo());
                                    }
                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 0, cd.getPostID(), c.getStatus(), c.getGame(), 0);
                                }
                                else {
                                    lembot.deleteMessage(announce_channel, cd.getPostID());
                                    cd.setPostID(null);
                                    dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 0, 0L, c.getStatus(), c.getGame(), 0);
                                }
                                cd.setOffline_flag(0);
                                cd.setLive(false);
                                cd.setGame(c.getGame());
                                cd.setTitle(c.getStatus());
                            }
                        }
                        else if (cd.getOffline_flag() != 0) {
                            cd.setOffline_flag(0);
                            dbHandler.updateChannelForGuild(g.getGuild_id(), cd.getChannelID(), c.getName(), 1, cd.getPostID(), c.getStatus(), c.getGame(), 0);
                        }
                    }
                }
                catch (Exception e) {
                    announcerLogger.error("Error occured during announcements of guild {} and channel {}", g.getGuild_id(), c.getName(), e);
                }
            }
        }
        streamSemaphore.release();
        announcerLogger.info("Announcer for guild {} was successful", g.getGuild_id());
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

    private EmbedObject buildEmbedMessage(String channelName, String game, String title, String iconUrl) {
        EmbedBuilder builder = new EmbedBuilder();

        if (game != null) {
            builder.appendField("Playing", game, true);
        }

        if (title != null) {
            builder.appendField("Title", title, false);
        }

        builder.withAuthorName(channelName + " has gone live!");
        builder.withAuthorIcon(twitchIcon);
        builder.withAuthorUrl("https://twitch.tv/" + channelName);
        builder.withColor(100, 65, 164);
        builder.withTitle("https://twitch.tv/" + channelName);
        builder.withUrl("https://twitch.tv/" + channelName);
        builder.withThumbnail(iconUrl);

        return builder.build();
    }

    private EmbedObject buildEmbedOffMessage(String channelName, String game, String title, String iconUrl) {
        EmbedBuilder builder = new EmbedBuilder();

        if (game != null) {
            builder.appendField("Game", game, true);
        }

        if (title != null) {
            builder.appendField("Title", title, false);
        }

        builder.withAuthorName("[OFFLINE]: " + channelName + " was streaming");
        builder.withAuthorIcon(twitchIcon);
        builder.withTitle("https://twitch.tv/" + channelName);
        builder.withUrl("https://twitch.tv/" + channelName);
        //     builder.withThumbnail(iconUrl);

        return builder.build();
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

    private Long sendClassicMessage(IChannel channel, String channelName, String game, String title) {
        return lembot.sendMessageID(channel, buildClassicMessage(channelName, game, title));
    }

    private void editClassicMessage(IChannel channel, Long postID, String channelName, String game, String title) {
        lembot.editMessage(channel, postID, buildClassicMessage(channelName, game, title));
    }

    private void editClassicOffMessage(IChannel channel, Long postID, String channelName, String game, String title) {
        lembot.editMessage(channel, postID, buildClassicOffMessage(channelName, game, title));
    }

    private Long sendEmbedMessage(IChannel channel, String channelName, String game, String title, String iconUrl) {
        return lembot.sendMessageID(channel, buildEmbedMessage(channelName, game, title, iconUrl));
    }

    private void editEmbedMessage(IChannel channel, Long postID, String channelName, String game, String title, String iconUrl) {
        lembot.editMessage(channel, postID, buildEmbedMessage(channelName, game, title, iconUrl));
    }

    private void editEmbedOffMessage(IChannel channel, Long postID, String channelName, String game, String title, String iconUrl) {
        lembot.editMessage(channel, postID, buildEmbedOffMessage(channelName, game, title, iconUrl));
    }

    private String toLowerCase(String input) {
        if (input == null) {
            return null;
        }
        else {
            return input.toLowerCase();
        }
    }
}

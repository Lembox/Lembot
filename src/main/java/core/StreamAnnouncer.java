package core;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import me.philippheuer.twitch4j.endpoints.StreamEndpoint;
import me.philippheuer.twitch4j.model.Channel;
import models.GuildStructure;
import models.ChannelDels;
import sx.blah.discord.handle.obj.IChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StreamAnnouncer {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    List<GuildStructure> allChannels;

    public StreamAnnouncer(List<GuildStructure> allChannels) {
        this.allChannels = allChannels;

        scheduler.scheduleAtFixedRate(() -> {
            announceStreams();
        }, 0L,1L, TimeUnit.MINUTES);

    }

    public void announceStreams() {
        for (GuildStructure g : allChannels) {
            IChannel announce_channel = Lembot.getDiscordClient().getChannelByID(g.getAnnounce_channel());
            List<ChannelEndpoint> channelsToBeAdded = g.getChannelsToBeAdded();
            List<ChannelDels> channelsToBeRemoved = g.getChannelsToBeRemoved();

            if (!channelsToBeAdded.isEmpty()) {
                for (ChannelEndpoint ce : channelsToBeAdded) {
                    g.addTwitch_channels(new ChannelDels(ce.getChannelId(), ce.getChannel().getName(), false, null, ce.getChannel().getStatus(), ce.getChannel().getGame(), 0, ce));
                    g.setChannelsToBeAdded(new ArrayList<>());
                }
            }
            if (!channelsToBeRemoved.isEmpty()) {
                for (ChannelDels cd : channelsToBeRemoved) {
                    g.removeTwitch_channels(cd);
                    g.setChannelsToBeRemoved(new ArrayList<>());
                }
            }

            List<ChannelDels> channelDels = g.getTwitch_channels();
            List<String> games = gamesLowerCase(g.getGame_filters());
            StreamEndpoint streamEndpoint;

            for (ChannelDels cd : channelDels) {
                ChannelEndpoint channelEndpoint = cd.getChannelEndpoint();

                if (channelEndpoint == null) {
                    cd.setChannelEndpoint(Lembot.getTwitchClient().getChannelEndpoint(cd.getChannelID()));
                }
                else {
                    streamEndpoint = Lembot.getTwitchClient().getStreamEndpoint();

                    Channel c = channelEndpoint.getChannel();
                    // Channel was offline and goes live
                    if (!cd.getLive()) {
                        try {
                            if ((streamEndpoint.isLive(c) && games.contains(c.getGame().toLowerCase())) || (streamEndpoint.isLive(c) && games.isEmpty())) {
                                Long id = Lembot.sendMessageID(announce_channel, c.getName() + " has gone live, streaming " + c.getGame() + ": **" + c.getStatus() + "**\n" + "http://twitch.tv/" + c.getName());

                                // Write to DB in case bot has to go offline and save to local variables
                                Lembot.getDbHandler().updateChannelForGuild(g.getGuild_id(), channelEndpoint.getChannel().getId(), c.getName(), 1, id, c.getStatus(), c.getGame(), 0);

                                cd.setLive(true);
                                cd.setPostID(id);
                                cd.setTitle(c.getStatus());
                                cd.setGame(c.getGame());
                                cd.setOffline_flag(0);
                            }
                        } catch (Exception e) {
                            streamEndpoint = Lembot.getTwitchClient().getStreamEndpoint();
                            e.printStackTrace();
                        }
                    }
                    // Channel is or was already live
                    else {
                        if (!streamEndpoint.isLive(c)) {
                            if (cd.getOffline_flag() < 3) {      // 3 minute chance to go back live
                                cd.setOffline_flag(cd.getOffline_flag() + 1);
                                Lembot.getDbHandler().updateChannelForGuild(g.getGuild_id(), channelEndpoint.getChannelId(), c.getName(), 1, cd.getPostID(), c.getStatus(), c.getGame(), cd.getOffline_flag() + 1);
                            }
                            else {
                                cd.setLive(false);
                                cd.setOffline_flag(0);
                                if(!g.getCleanup()) {       // no cleanup -> offline messages
                                    Lembot.editMessage(announce_channel, cd.getPostID(), "[OFFLINE]: " + c.getName() + " was streaming " + cd.getGame() + ": **" + cd.getTitle() + "**\n" + "<http://twitch.tv/" + c.getName() + ">");
                                }
                                else {
                                    Lembot.deleteMessage(announce_channel, cd.getPostID());
                                    cd.setPostID(null);
                                }
                                Lembot.getDbHandler().updateChannelForGuild(g.getGuild_id(), channelEndpoint.getChannelId(), c.getName(), 0, cd.getPostID(), c.getStatus(), c.getGame(), 0);
                            }
                        }
                        else {
                            if (!cd.getGame().equals(c.getGame())) {
                                if (games.contains(c.getGame().toLowerCase()) || games.isEmpty()) {      // Streamed followed game and changes to followed game
                                    Lembot.editMessage(announce_channel, cd.getPostID(), c.getName() + " has gone live, streaming " + c.getGame() + ": **" + c.getStatus() + "**\n" + "http://twitch.tv/" + c.getName());
                                    Lembot.getDbHandler().updateChannelForGuild(g.getGuild_id(), channelEndpoint.getChannelId(), c.getName(), 1, cd.getPostID(), c.getStatus(), c.getGame(), 0);

                                    cd.setGame(c.getGame());
                                    cd.setTitle(c.getStatus());
                                    cd.setOffline_flag(0);
                                }
                                else {              // Streamed followed game and changes to not followed game
                                    if (!g.getCleanup()) {
                                        Lembot.editMessage(announce_channel, cd.getPostID(), "[OFFLINE]: " + c.getName() + " was streaming " + cd.getGame() + ": **" + cd.getTitle() + "**\n" + "<http://twitch.tv/" + c.getName() + ">");
                                    }
                                    else {
                                        Lembot.deleteMessage(announce_channel, cd.getPostID());
                                        cd.setPostID(null);
                                    }
                                    Lembot.getDbHandler().updateChannelForGuild(g.getGuild_id(), channelEndpoint.getChannelId(), c.getName(), 0, cd.getPostID(), c.getStatus(), c.getGame(), 0);

                                    cd.setOffline_flag(0);
                                    cd.setLive(false);
                                    cd.setGame(c.getGame());
                                    cd.setTitle(c.getStatus());
                                }
                            }
                            else if (cd.getOffline_flag() != 0) {
                                cd.setOffline_flag(0);
                                Lembot.getDbHandler().updateChannelForGuild(g.getGuild_id(), channelEndpoint.getChannelId(), c.getName(), 1, cd.getPostID(), c.getStatus(), c.getGame(), 0);

                            }
                        }

                    }

                }
            }
        }
    }

    public void restartScheduler() {
        scheduler.shutdown();

        scheduler.scheduleAtFixedRate(() -> {
            announceStreams();
        },0,1, TimeUnit.MINUTES);
    }

    public List<String> gamesLowerCase(List<String> games) {
        List<String> resultingGames = new ArrayList<>();

        for (String g : games) {
            resultingGames.add(g.toLowerCase());
        }

        return resultingGames;
    }
}

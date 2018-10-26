package core;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import me.philippheuer.twitch4j.endpoints.StreamEndpoint;
import models.ChannelDels;
import models.GuildStructure;
import listeners.DiscordHandler;
import listeners.GuildHandler;
import listeners.MessageHandler;

import me.philippheuer.twitch4j.TwitchClientBuilder;
import me.philippheuer.twitch4j.TwitchClient;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.RequestBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;

public class Lembot {
    private TwitchClient twitchClient;
    private IDiscordClient discordClient;

    private ChannelEndpoint channelEndpoint;
    private StreamEndpoint streamEndpoint;

    private Logger logger = LoggerFactory.getLogger(Lembot.class);

    private DBHandler dbHandler;
    private List<GuildStructure> allChannels = new ArrayList<>();

    private Semaphore guildSemaphore = new Semaphore(1);     // concurrent access on guild structures

    public Lembot() {
        Properties properties = new Properties();
        String db_path;

        try {
            properties.load(new FileReader("bot_config.txt"));   // get config from file
        }
        catch (IOException e) {
            logger.error("Config file could not be loaded", e);
        }

        try {
            db_path = properties.get("db_path").toString();
        }
        catch (NullPointerException e) {
            db_path = null;
        }

        dbHandler = new DBHandler(db_path);
        try {
            discordClient = new ClientBuilder()
                    .withToken(properties.get("discord_token").toString())
                    .withRecommendedShardCount()
                    .setMaxReconnectAttempts(Integer.MAX_VALUE)
                    .login();

            EventDispatcher dispatcher = discordClient.getDispatcher();
            dispatcher.registerListener(new GuildHandler(this));
            dispatcher.registerListener(new MessageHandler(this));
            dispatcher.registerListener(new DiscordHandler(this));
        }
        catch (DiscordException de) {
            logger.error("Discord client could not be set up", de);
        }

        twitchClient = TwitchClientBuilder.init()
                .withClientId(properties.get("twitch_clientID").toString())
                .withClientSecret(properties.get("twitch_clientSecret").toString())
                .withAutoSaveConfiguration(true)
                .withConfigurationDirectory(new File("config"))
                .withCredential(properties.get("twitch_oauth").toString())
                .build();
    }

    public void init() {
        // Read necessary information from DB
        List<GuildStructure> guilds = dbHandler.getGuilds();
        List<IGuild> connected_guilds = discordClient.getGuilds();
        Long guildID;
        List<ChannelDels> twitch_channels;
        List<String> gameFilters;

        List<Long> connected_guildIDs = new ArrayList<>();

        for (IGuild iGuild : connected_guilds) {
            connected_guildIDs.add(iGuild.getLongID());
        }

        try {
            guildSemaphore.acquire();
        } catch (InterruptedException e) {
            logger.error("Initialization failed due to interrupted access of the guildSemaphore", e);
        }

        for (GuildStructure g : guilds) {
            guildID = g.getGuild_id();

            // if bot was kicked during an offline period
            if (!connected_guildIDs.contains(guildID)) {
                dbHandler.removeGuild(guildID);
                removeGuildStructure(guildID);
            }
            else {
                twitch_channels = new ArrayList<>(dbHandler.getChannelsForGuild(guildID));
                gameFilters = new ArrayList<>(dbHandler.getGamesForGuild(guildID));

                g.setGame_filters(gameFilters);
                g.setTwitch_channels(twitch_channels);
                g.setLembot(this);
                g.setAnnouncer(new StreamAnnouncer(g));
                allChannels.add(g);
            }
        }

        guildSemaphore.release();

        while (channelEndpoint == null) {
            try {
                channelEndpoint = twitchClient.getChannelEndpoint();
            } catch (Exception e) {
                logger.warn("Channel Endpoint Exception occured", e);
            }

        }
        while (streamEndpoint == null) {
            try {
                streamEndpoint = twitchClient.getStreamEndpoint();
            } catch (Exception e) {
                logger.warn("Stream Endpoint Exception occured", e);
            }
        }
        logger.info("Guilds reinitialized");
    }

    public ChannelEndpoint getChannelEndpoint() {
        return channelEndpoint;
    }

    public StreamEndpoint getStreamEndpoint() {
        return streamEndpoint;
    }

    public TwitchClient getTwitchClient() {
        return twitchClient;
    }

    public IDiscordClient getDiscordClient() {
        return discordClient;
    }

    public DBHandler getDbHandler() {
        return dbHandler;
    }

    public void addGuildStructure(GuildStructure guildStructure) {
        try {
            guildSemaphore.acquire();
        }
        catch (InterruptedException e) {
            logger.error("Adding guild structure for guild {} failed due to interrupted access of the guildSemaphore", guildStructure.getGuild_id(), e);
        }
        allChannels.add(guildStructure);
        guildSemaphore.release();
    }

    public GuildStructure provideGuildStructure(Long guildID) {
        try {
            guildSemaphore.acquire();
        } catch (InterruptedException e) {
            logger.error("Providing guild structure for guild {} failed due to interrupted access of the guildSemaphore", guildID, e);
        }

        for (GuildStructure g : allChannels) {
            if (g.getGuild_id().equals(guildID)) {
                guildSemaphore.release();
                return g;
            }
        }
        guildSemaphore.release();
        return null;
    }

    public void sendMessage(IChannel channel, String message) {
        RequestBuffer.request(() -> {
            try{
                channel.sendMessage(message);
            } catch (RateLimitException e){
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to rate limitations.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), e);
                throw e; // This makes sure that RequestBuffer will do the retry for you
            } catch (MissingPermissionsException mpe) {
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), mpe);
            }
        });
    }

    public void sendMessage(IChannel channel, EmbedObject message) {
        RequestBuffer.request(() -> {
            try{
                channel.sendMessage(message);
            } catch (RateLimitException e){
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to rate limitations.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), e);
                throw e; // This makes sure that RequestBuffer will do the retry for you
            } catch (MissingPermissionsException mpe) {
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), mpe);
            }
        });
    }

    public Long sendMessageID(IChannel channel, EmbedObject message) {
        RequestBuffer.RequestFuture<Long> id = RequestBuffer.request(() -> {
            try{
                return channel.sendMessage(message).getLongID();
            } catch (RateLimitException e){
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to rate limitations.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), e);
                throw e; // This makes sure that RequestBuffer will do the retry for you
            } catch (MissingPermissionsException mpe) {
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), mpe);
                return null;
            }
        });
        return id.get();
    }

    public Long sendMessageID(IChannel channel, String message) {
        RequestBuffer.RequestFuture<Long> id = RequestBuffer.request(() -> {
            try{
                return channel.sendMessage(message).getLongID();
            } catch (RateLimitException e){
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to rate limitations.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), e);
                throw e; // This makes sure that RequestBuffer will do the retry for you
            } catch (MissingPermissionsException mpe) {
                logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, channel.getName(), channel.getLongID(), channel.getGuild().getName(), channel.getGuild().getLongID(), mpe);
                return null;
            }
        });
        return id.get();
    }

    public void editMessage(IChannel channel, Long messageID, EmbedObject message) {
        RequestBuffer.request(() -> {
            try {
                try {
                    channel.getMessageByID(messageID).edit(message);
                }
                catch (NullPointerException e) {
                    try {
                        channel.fetchMessage(messageID).edit(message);
                    }
                    catch (NullPointerException ne) {
                        logger.error("Message: {} could not be edited because channel was changed.", messageID, ne);
                    }
                }
            }
            catch (RateLimitException re) {
                logger.error("Message: {} could not be edited due to rate limitations.", messageID, re);
                throw re;
            }
            catch (MissingPermissionsException mpe) {
                logger.error("Message: {} could not be edited due to missing permissions.", messageID, mpe);
            }
        });
    }

    public void editMessage(IChannel channel, Long messageID, String message) {
        RequestBuffer.request(() -> {
            try {
                try {
                    channel.getMessageByID(messageID).edit(message);
                }
                catch (NullPointerException e) {
                    try {
                        channel.fetchMessage(messageID).edit(message);
                    }
                    catch (NullPointerException ne) {
                        logger.error("Message: {} could not be edited because channel was changed.", messageID, ne);
                    }
                }
            }
            catch (RateLimitException re) {
                logger.error("Message: {} could not be edited due to rate limitations.", messageID, re);
                throw re;
            }
        });
    }

    public void deleteMessage(IChannel channel, Long messageID) {
        RequestBuffer.request(() -> {
            try {
                try {
                    channel.getMessageByID(messageID).delete();
                }
                catch (NullPointerException e) {
                    try {
                        channel.fetchMessage(messageID).delete();
                    }
                    catch (NullPointerException ne) {
                        logger.error("Message: {} could not be deleted because channel was changed.", messageID, ne);
                    }
                }
            }
            catch (RateLimitException re) {
                logger.error("Message: {} could not be deleted due to rate limitations.", messageID, re);
                throw re;
            } catch (MissingPermissionsException mpe) {
                logger.error("Message: {} could not be deleted due to missing permissions.", messageID, mpe);
            }
        });
    }

    public void removeGuildStructure(Long guildID) {
        try {
            guildSemaphore.acquire();
        } catch (InterruptedException e) {
            logger.error("Removing guild structure for guild {} failed due to interrupted access of the guildSemaphore", guildID, e);
        }

        GuildStructure guildStructure = null;
        for (GuildStructure g : allChannels) {
            if (g.getGuild_id().equals(guildID)) {
                guildStructure = g;
                break;
            }
        }

        guildStructure.getAnnouncer().shutdownScheduler();
        guildStructure.setAnnouncer(null);      // "manual" garbage collection
        guildStructure.setTwitch_channels(null);
        guildStructure.setGame_filters(null);

        allChannels.remove(guildStructure);

        guildSemaphore.release();
    }

    public void announceOfftime() {
        try {
            guildSemaphore.acquire();
        } catch (InterruptedException e) {
            logger.error("Announcing offtime failed due to interrupted access of the guildSemaphore", e);
        }

        for (GuildStructure g : allChannels) {
            try {
                g.getAnnouncer().getStreamSemaphore().acquire();
            }
            catch (InterruptedException e) {
                logger.error("Shutting down scheduler of guild {} failed due to interrupted access of the streamSemaphore", g.getGuild_id(), e);
            }

            try {
                discordClient.getChannelByID(g.getAnnounce_channel()).sendMessage("The bot is going offline for a while to push an update, fix or similar.");
            } catch (RateLimitException re) {
                logger.error("Offtime message to annoucement channel (id: {}) from guild (id: {}) could not be sent due to rate limitations.", g.getAnnounce_channel(), g.getGuild_id(), re);
            } catch (MissingPermissionsException mpe) {
                logger.error("Offtime message to annoucement channel (id: {}) from guild (id: {}) could not be sent due to missing permissions.", g.getAnnounce_channel(), g.getGuild_id(), mpe);
            }

            g.getAnnouncer().shutdownScheduler();
            g.getAnnouncer().getStreamSemaphore().release();
        }
        guildSemaphore.release();
    }

    public void forceShutdown() {
        for (GuildStructure g : allChannels) {
            g.getAnnouncer().shutdownScheduler();
        }
        guildSemaphore = new Semaphore(1);
    }

    public void restartAfterOutage() {
        try {
            guildSemaphore.acquire();
        } catch (InterruptedException e) {
            logger.error("Restarting after outage failed due to interrupted access of the guildSemaphore", e);
        }

        for (GuildStructure g : allChannels) {
            g.getAnnouncer().restartScheduler();
        }
        guildSemaphore.release();
    }

    public Logger getLogger() { return logger; }
}

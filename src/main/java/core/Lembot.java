package core;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.GameList;

import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.UserList;

import models.ChannelDels;
import models.GuildStructure;
import listeners.DiscordHandler;
import listeners.GuildHandler;
import listeners.MessageHandler;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

public class Lembot {
    private TwitchClient twitchClient;

    private DiscordApi discordApi;
    private Properties properties;
    private Boolean initialized = false;

    private Logger logger = LoggerFactory.getLogger(Lembot.class);

    private DBHandler dbHandler;
    private List<GuildStructure> allChannels = new ArrayList<>();

    private Semaphore guildSemaphore = new Semaphore(1);     // concurrent access on guild structures

    public Lembot() {
        properties = new Properties();
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
            discordApi = new DiscordApiBuilder()
                    .setToken(properties.get("discord_token").toString())
                    .setAllNonPrivilegedIntents()
                    .addIntents(Intent.MESSAGE_CONTENT)
                    .login()
                    .join();

            discordApi.addListener(new MessageHandler(this));
            discordApi.addListener(new GuildHandler(this));
            discordApi.addListener(new DiscordHandler(this));

        }
        catch (Exception de) {
            logger.error("Discord client could not be set up", de);
        }

        twitchClient = TwitchClientBuilder.builder()
                .withClientId(properties.get("twitch_clientID").toString())
                .withClientSecret(properties.get("twitch_clientSecret").toString())
                .withEnableHelix(true)
                .build();

        init();
    }

    public void init() {
        System.out.println("init");
        // Read necessary information from DB
        List<GuildStructure> guilds = dbHandler.getGuilds();
        List<Server> connected_guilds = new ArrayList<Server>(discordApi.getServers());
        Long guildID;
        List<ChannelDels> twitch_channels;
        Map<String, String> gameFilters;

        List<Long> connected_guildIDs = new ArrayList<>();

        for (Server guild : connected_guilds) {
            connected_guildIDs.add(guild.getId());
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
                connected_guildIDs.remove(guildID); // remove guild that is already connected
                twitch_channels = new ArrayList<>(dbHandler.getChannelsForGuild(guildID));
                List<String> filterList = new ArrayList<>(dbHandler.getGamesForGuild(guildID));

                gameFilters = new HashMap<>();
                Boolean repeat = false;

                if (!filterList.isEmpty()) {
                    try {
                        GameList games = getGames(null, filterList);
                        List<Game> gameList = games.getGames();

                        do {
                            for (Game ga : gameList) {
                                gameFilters.put(ga.getId(), ga.getName());
                                if (ga.getId() == null) {
                                    repeat = true;
                                    break;
                                }
                            }
                        } while (repeat);

                    } catch (Exception e) {
                        logger.error("Error occured", e);
                    }

                }

                g.setGame_filters(gameFilters);
                g.setTwitch_channels(twitch_channels);
                g.setLembot(this);
                g.setAnnouncer(new StreamAnnouncer(g));
                allChannels.add(g);
            }
        }

        // guilds that are connected but not in DB
        for (Long guild_id : connected_guildIDs) {
            Server guild = discordApi.getServerById(guild_id).get();
            Long guild_id_in_db = dbHandler.getGuild(guild);

            if (guild_id_in_db == null) {
                getLogger().info("New guild {} with name {} joined", guild.getId(), guild.getName());
                dbHandler.addTableForGuild(guild);
                dbHandler.addGuild(guild);
                dbHandler.addOwnerAsMaintainer(guild);

                Long announce_channel = null;

                GuildStructure guildStructure = new GuildStructure(guild_id, new ArrayList<>(), new HashMap<>(), announce_channel, this);
                guildStructure.setAnnouncer(new StreamAnnouncer(guildStructure));
                addGuildStructure(guildStructure);

                try {
                    PrivateChannel privateChannelToOwner = getDiscordApi().getUserById(guild.getOwnerId()).get().getPrivateChannel().get();
                    privateChannelToOwner.sendMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init in a channel of your server/guild with rights for me to read and write in for more information");
                }
                catch (Exception e) {
                    getLogger().error("Error occured while joining guild {}", guild_id, e);
                }
            }
            else {
                getLogger().info("Rejoined guild {} with name {}", guild_id, guild.getName());
            }
        }

        guildSemaphore.release();
        initialized = true;
        logger.info("Guilds reinitialized");
        System.out.println("Guilds reinitialized");
    }

    public TwitchClient getTwitchClient() {
        return twitchClient;
    }

    public DiscordApi getDiscordApi() {
        return discordApi;
    }

    public DBHandler getDbHandler() {
        return dbHandler;
    }

    public Boolean isInitialized() {
        return initialized;
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

    public void sendMessage(ServerTextChannel channel, String message) {
        try {
            channel.sendMessage(message);
        } catch (Exception e) {
            logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, channel.getName(), channel.getId(), channel.getServer().getName(), channel.getServer().getId(), e);
        }
    }

    public void sendMessage(ServerTextChannel channel, EmbedBuilder embedBuilder) {
        try {
            channel.sendMessage(embedBuilder);
        } catch (Exception e) {
            logger.error("Embedded message to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", channel.getName(), channel.getId(), channel.getServer().getName(), channel.getServer().getId(), e);
        }
    }

    public Long sendMessageID(ServerTextChannel channel, String message) {
        try {
            return channel.sendMessage(message).get().getId();
        } catch (Exception e) {
            logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, channel.getName(), channel.getId(), channel.getServer().getName(), channel.getServer().getId(), e);
            return null;
        }
    }

    public Long sendMessageID(ServerTextChannel channel, EmbedBuilder embedBuilder) {
        try {
            return channel.sendMessage(embedBuilder).get().getId();
        } catch (Exception e) {
            logger.error("Embedded message to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", channel.getName(), channel.getId(), channel.getServer().getName(), channel.getServer().getId(), e);
            return null;
        }
    }

    public void editMessage(ServerTextChannel channel, Long messageID, EmbedBuilder embedBuilder) {
        try {
            channel.getMessageById(messageID).get().edit(embedBuilder);
        } catch (Exception e) {
            logger.error("Message: {} could not be edited due to missing permissions.", messageID, e);
        }
    }

    public void editMessage(ServerTextChannel channel, Long messageID, String message) {
        try {
            channel.getMessageById(messageID).get().edit(message);
        } catch (Exception e) {
            logger.error("Message: {} could not be edited due to missing permissions.", messageID, e);
        }
    }

    public void deleteMessage(ServerTextChannel channel, Long messageID) {
        try {
            channel.getMessageById(messageID).get().delete();
        } catch (Exception npe) {
            logger.error("Message: {} could not be deleted due to an error", messageID, npe);
        }
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
        guildStructure.setLembot(null);

        allChannels.remove(guildStructure);

        guildSemaphore.release();
    }

    public UserList getUsers(List<String> channelIDs, List<String> channelNames) throws Exception {
        return twitchClient.getHelix().getUsers(properties.getProperty("twitch_oauth"), channelIDs, channelNames).execute();
    }

    public StreamList getStreams(String after, List<String> gameIDs, List<String> channelIDs) throws Exception {
        return twitchClient.getHelix().getStreams(properties.getProperty("twitch_oauth"), after, "", null, null, gameIDs, null, channelIDs, null).execute();
    }

    public GameList getGames(List<String> gameIDs, List<String> gameNames) throws Exception {
        return twitchClient.getHelix().getGames(properties.getProperty("twitch_oauth"), gameIDs, gameNames).execute();
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
                ServerTextChannel announceChannel = discordApi.getChannelById(g.getAnnounce_channel()).get().asServerTextChannel().get();
                announceChannel.sendMessage("The bot is going offline for a while to push an update, fix or similar.");
            } catch (Exception mpe) {
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

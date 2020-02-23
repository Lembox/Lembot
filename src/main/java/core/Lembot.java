package core;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.GameList;

import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.UserList;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.guild.GuildUpdateEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.event.domain.lifecycle.ResumeEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.gateway.retry.RetryOptions;
import discord4j.rest.http.client.ClientException;

import models.ChannelDels;
import models.GuildStructure;
import listeners.DiscordHandler;
import listeners.GuildHandler;
import listeners.MessageHandler;

import reactor.core.scheduler.Schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class Lembot {
    private TwitchClient twitchClient;
    private DiscordClient discordClient;
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
            discordClient = new DiscordClientBuilder(properties.get("discord_token").toString())
                    .setRetryOptions(new RetryOptions(Duration.ofSeconds(5), Duration.ofSeconds(120),
                            Integer.MAX_VALUE, Schedulers.elastic()))
                    .build();

            discordClient.login().subscribe();

            MessageHandler messageHandler = new MessageHandler(this);
            GuildHandler guildHandler = new GuildHandler(this);
            DiscordHandler discordHandler = new DiscordHandler(this);

            EventDispatcher dispatcher = discordClient.getEventDispatcher();
            dispatcher.on(MessageCreateEvent.class).subscribe(event -> messageHandler.onMessageEvent(event.getMessage()));
            dispatcher.on(GuildCreateEvent.class).subscribe(event -> guildHandler.onGuildJoined(event.getGuild()));
            dispatcher.on(GuildDeleteEvent.class).subscribe(event -> guildHandler.onGuildLeft(event.getGuild().orElse(null), event.isUnavailable()));
            dispatcher.on(GuildUpdateEvent.class).subscribe(event -> guildHandler.onGuildUpdate(event.getOld().orElse(null), event.getCurrent()));
            dispatcher.on(TextChannelDeleteEvent.class).subscribe(event -> guildHandler.onChannelDeleted(event.getChannel()));
            dispatcher.on(DisconnectEvent.class).subscribe(discordHandler::onDisconnected);
            dispatcher.on(ReconnectEvent.class).subscribe(discordHandler::onReconnected);
            dispatcher.on(ReadyEvent.class).subscribe(discordHandler::onReady);
            dispatcher.on(ResumeEvent.class).subscribe(discordHandler::onResumed);
        }
        catch (ClientException de) {
            logger.error("Discord client could not be set up", de);
        }

        twitchClient = TwitchClientBuilder.builder()
                .withClientId(properties.get("twitch_clientID").toString())
                .withClientSecret(properties.get("twitch_clientSecret").toString())
                .withEnableHelix(true)
                .build();
    }

    public void init() {
        System.out.println("init");
        // Read necessary information from DB
        List<GuildStructure> guilds = dbHandler.getGuilds();
        List<Guild> connected_guilds = discordClient.getGuilds().collectList().block();
        Long guildID;
        List<ChannelDels> twitch_channels;
        Map<String, String> gameFilters;

        List<Long> connected_guildIDs = new ArrayList<>();

        for (Guild guild : connected_guilds) {
            connected_guildIDs.add(guild.getId().asLong());
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

        guildSemaphore.release();
        initialized = true;
        logger.info("Guilds reinitialized");
        System.out.println("Guilds reinitialized");
    }

    public TwitchClient getTwitchClient() {
        return twitchClient;
    }

    public DiscordClient getDiscordClient() {
        return discordClient;
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

    public void sendMessage(Channel channel, String message) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            textChannel.createMessage(message).subscribe();
        } catch (ClientException e) {
            logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, textChannel.getName(), textChannel.getId().asLong(), textChannel.getGuild().block().getName(), textChannel.getGuild().block().getId().asLong(), e);
        }
    }

    public void sendMessage(Channel channel, Consumer<EmbedCreateSpec> embedCreateSpecConsumer) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            textChannel.createEmbed(embedCreateSpecConsumer).subscribe();
        } catch (ClientException e) {
            logger.error("Embedded message to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", textChannel.getName(), textChannel.getId().asLong(), textChannel.getGuild().block().getName(), textChannel.getGuild().block().getId().asLong(), e);
        }
    }

    public Long sendMessageID(Channel channel, String message) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            return textChannel.createMessage(message).block().getId().asLong();
        } catch (ClientException e) {
            logger.error("Message: {} to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", message, textChannel.getName(), textChannel.getId().asLong(), textChannel.getGuild().block().getName(), textChannel.getGuild().block().getId().asLong(), e);
            return null;
        }
    }

    public Long sendMessageID(Channel channel, Consumer<EmbedCreateSpec> embedCreateSpecConsumer) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            return textChannel.createEmbed(embedCreateSpecConsumer).block().getId().asLong();
        } catch (ClientException e) {
            logger.error("Embedded message to channel {} (id: {}) from guild {} (id: {}) could not be sent due to missing permissions.", textChannel.getName(), textChannel.getId().asLong(), textChannel.getGuild().block().getName(), textChannel.getGuild().block().getId().asLong(), e);
            return null;
        }
    }

    public void editMessage(Channel channel, Long messageID, Consumer<EmbedCreateSpec> embedCreateSpecConsumer) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            textChannel.getMessageById(Snowflake.of(messageID)).block().edit(message -> message.setEmbed(embedCreateSpecConsumer)).subscribe();
        } catch (ClientException e) {
            logger.error("Message: {} could not be edited due to missing permissions.", messageID, e);
        }
    }

    public void editMessage(Channel channel, Long messageID, String message) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            textChannel.getMessageById(Snowflake.of(messageID)).block().edit(messageEditSpec -> messageEditSpec.setContent(message));
        } catch (ClientException e) {
            logger.error("Message: {} could not be edited due to missing permissions.", messageID, e);
        }
    }

    public void deleteMessage(Channel channel, Long messageID) {
        TextChannel textChannel = (TextChannel) channel;

        try {
            textChannel.getMessageById(Snowflake.of(messageID)).block().delete().subscribe();
        } catch (NullPointerException npe) {
            logger.error("Message: {} could not be deleted due to an error", messageID, npe);
        } catch (ClientException e) {
            logger.error("Message: {} could not be deleted due to missing permissions.", messageID, e);
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
                TextChannel announceChannel = (TextChannel) discordClient.getChannelById(Snowflake.of(g.getAnnounce_channel())).block();
                announceChannel.createMessage("The bot is going offline for a while to push an update, fix or similar.").block();
            } catch (ClientException mpe) {
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

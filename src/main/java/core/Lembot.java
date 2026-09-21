package core;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.GameList;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.UserList;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;

import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.guild.GuildUpdateEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.event.domain.lifecycle.ResumeEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;

import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;

import listeners.DiscordHandler;
import listeners.GuildHandler;
import listeners.MessageHandler;

import models.ChannelDels;
import models.GuildStructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class Lembot {

    private TwitchClient twitchClient;

    private DiscordClient discordClient;
    private GatewayDiscordClient gatewayDiscordClient;

    private Properties properties;

    private Boolean initialized = false;

    private final Logger logger =
            LoggerFactory.getLogger(Lembot.class);

    private DBHandler dbHandler;

    private final List<GuildStructure> allChannels =
            new ArrayList<>();

    /**
     * Protects access to allChannels.
     */
    private final Semaphore guildSemaphore =
            new Semaphore(1);

    public Lembot() {

        properties = new Properties();

        /*
         * -------------------------------------------------------------
         * Configuration
         * -------------------------------------------------------------
         */

        try {

            properties.load(
                    new FileReader("bot_config.txt")
            );

        } catch (IOException e) {

            logger.error(
                    "Config file could not be loaded",
                    e
            );
        }

        String dbPath =
                properties.getProperty("db_path");

        if (dbPath == null || dbPath.isBlank()) {

            logger.error(
                    "db_path is missing from bot_config.txt"
            );

            throw new IllegalStateException(
                    "db_path is missing from bot_config.txt"
            );
        }

        dbHandler =
                new DBHandler(dbPath);

        /*
         * -------------------------------------------------------------
         * Discord
         * -------------------------------------------------------------
         */

        try {

            String discordToken =
                    properties.getProperty("discord_token");

            if (discordToken == null ||
                    discordToken.isBlank()) {

                throw new IllegalStateException(
                        "discord_token is missing from bot_config.txt"
                );
            }

            discordClient =
                    DiscordClient.create(discordToken);

            /*
             * Discord4J 3.3:
             *
             * DiscordClient -> GatewayDiscordClient
             *
             * MESSAGE_CONTENT is required because the bot reads
             * message contents in MessageHandler.
             */
            gatewayDiscordClient =
                    discordClient
                            .gateway()
                            .setEnabledIntents(
                                    IntentSet
                                            .nonPrivileged()
                                            .or(
                                                    IntentSet.of(
                                                            Intent.MESSAGE_CONTENT
                                                    )
                                            )
                            )
                            .login()
                            .block();

            if (gatewayDiscordClient == null) {

                throw new IllegalStateException(
                        "Discord Gateway could not be initialized"
                );
            }

            /*
             * ---------------------------------------------------------
             * Event handlers
             * ---------------------------------------------------------
             */

            MessageHandler messageHandler =
                    new MessageHandler(this);

            GuildHandler guildHandler =
                    new GuildHandler(this);

            DiscordHandler discordHandler =
                    new DiscordHandler(this);

            gatewayDiscordClient
                    .on(MessageCreateEvent.class)
                    .subscribe(
                            event ->
                                    messageHandler.onMessageEvent(
                                            event.getMessage()
                                    )
                    );

            gatewayDiscordClient
                    .on(GuildCreateEvent.class)
                    .subscribe(
                            event ->
                                    guildHandler.onGuildJoined(
                                            event.getGuild()
                                    )
                    );

            gatewayDiscordClient
                    .on(GuildDeleteEvent.class)
                    .subscribe(
                            event ->
                                    guildHandler.onGuildLeft(
                                            event.getGuild().orElse(null),
                                            event.isUnavailable()
                                    )
                    );

            gatewayDiscordClient
                    .on(GuildUpdateEvent.class)
                    .subscribe(
                            event ->
                                    guildHandler.onGuildUpdate(
                                            event.getOld().orElse(null),
                                            event.getCurrent()
                                    )
                    );

            /*
             * NOTE:
             *
             * TextChannelDeleteEvent now supplies the Discord4J 3.3
             * channel type:
             *
             * discord4j.core.object.entity.channel.TextChannel
             *
             * GuildHandler must therefore be migrated to this same
             * type. The event registration itself is correct.
             */
            gatewayDiscordClient
                    .on(TextChannelDeleteEvent.class)
                    .subscribe(
                            event ->
                                    guildHandler.onChannelDeleted(
                                            event.getChannel()
                                    )
                    );

            gatewayDiscordClient
                    .on(DisconnectEvent.class)
                    .subscribe(
                            discordHandler::onDisconnected
                    );

            gatewayDiscordClient
                    .on(ReconnectEvent.class)
                    .subscribe(
                            discordHandler::onReconnected
                    );

            gatewayDiscordClient
                    .on(ReadyEvent.class)
                    .subscribe(
                            event -> {
                                logger.info("=== READY EVENT RECEIVED IN LEMBOT ===");

                                try {
                                    discordHandler.onReady(event);
                                } catch (Exception e) {
                                    logger.error(
                                            "Exception inside DiscordHandler.onReady()",
                                            e
                                    );
                                }
                            },
                            error -> logger.error(
                                    "ERROR IN READY EVENT STREAM",
                                    error
                            )
                    );

            gatewayDiscordClient
                    .on(ResumeEvent.class)
                    .subscribe(
                            discordHandler::onResumed
                    );

            logger.info("Calling lembot.init() directly...");
            init();
            logger.info("lembot.init() finished");

            logger.info("Discord client initialized");

            logger.info(
                    "Discord client initialized"
            );

        } catch (Exception e) {

            logger.error(
                    "Discord client could not be set up",
                    e
            );
        }

        /*
         * -------------------------------------------------------------
         * Twitch
         * -------------------------------------------------------------
         */

        try {

            String twitchClientId =
                    properties.getProperty(
                            "twitch_clientID"
                    );

            String twitchClientSecret =
                    properties.getProperty(
                            "twitch_clientSecret"
                    );

            if (twitchClientId == null ||
                    twitchClientId.isBlank()) {

                throw new IllegalStateException(
                        "twitch_clientID is missing from bot_config.txt"
                );
            }

            if (twitchClientSecret == null ||
                    twitchClientSecret.isBlank()) {

                throw new IllegalStateException(
                        "twitch_clientSecret is missing from bot_config.txt"
                );
            }

            twitchClient =
                    TwitchClientBuilder.builder()
                            .withClientId(twitchClientId)
                            .withClientSecret(twitchClientSecret)
                            .withEnableHelix(true)
                            .build();

            logger.info(
                    "Twitch client initialized"
            );

        } catch (Exception e) {

            logger.error(
                    "Twitch client could not be set up",
                    e
            );
        }
    }

    /*
     * -----------------------------------------------------------------
     * Initialization
     * -----------------------------------------------------------------
     */

    public void init() {
        logger.info("=== LEMBOT INIT START ===");

        List<GuildStructure> guilds =
                dbHandler.getGuilds();

        logger.info("DB returned {} guild structures", guilds.size());

        if (gatewayDiscordClient == null) {

            logger.error(
                    "Cannot initialize guilds because Discord Gateway is not available"
            );

            return;
        }

        List<discord4j.core.object.entity.Guild> connectedGuilds =
                gatewayDiscordClient
                        .getGuilds()
                        .collectList()
                        .block();

        if (connectedGuilds == null) {

            logger.error(
                    "Could not retrieve connected Discord guilds"
            );

            return;
        }

        List<Long> connectedGuildIds =
                new ArrayList<>();

        for (
                discord4j.core.object.entity.Guild guild :
                connectedGuilds
        ) {

            connectedGuildIds.add(
                    guild.getId().asLong()
            );
        }

        boolean acquired = false;

        try {

            guildSemaphore.acquire();
            acquired = true;

            for (GuildStructure guildStructure : guilds) {

                logger.info(
                        "DB GuildStructure: {}",
                        guildStructure.getGuild_id()
                );

                Long guildId =
                        guildStructure.getGuild_id();

                /*
                 * Bot was kicked while offline.
                 */
                if (!connectedGuildIds.contains(guildId)) {

                    dbHandler.removeGuild(guildId);

                    removeGuildStructureInternal(
                            guildId
                    );

                    continue;
                }

                /*
                 * -----------------------------------------------------
                 * Twitch channels
                 * -----------------------------------------------------
                 */

                List<ChannelDels> twitchChannels =
                        new ArrayList<>(
                                dbHandler.getChannelsForGuild(
                                        guildId
                                )
                        );

                /*
                 * -----------------------------------------------------
                 * Game filters
                 * -----------------------------------------------------
                 */

                List<String> filterList =
                        new ArrayList<>(
                                dbHandler.getGamesForGuild(
                                        guildId
                                )
                        );

                Map<String, String> gameFilters =
                        new HashMap<>();

                if (!filterList.isEmpty()) {

                    try {

                        GameList games =
                                getGames(
                                        null,
                                        filterList
                                );

                        if (games != null &&
                                games.getGames() != null) {

                            for (
                                    Game game :
                                    games.getGames()
                            ) {

                                if (game.getId() != null) {

                                    gameFilters.put(
                                            game.getId(),
                                            game.getName()
                                    );
                                }
                            }
                        }

                    } catch (Exception e) {

                        logger.error(
                                "Error while loading game filters for guild {}",
                                guildId,
                                e
                        );
                    }
                }

                /*
                 * -----------------------------------------------------
                 * Build runtime guild structure
                 * -----------------------------------------------------
                 */

                guildStructure.setGame_filters(
                        gameFilters
                );

                guildStructure.setTwitch_channels(
                        twitchChannels
                );

                guildStructure.setLembot(
                        this
                );

                guildStructure.setAnnouncer(
                        new StreamAnnouncer(
                                guildStructure
                        )
                );

                logger.info(
                        "Adding guild {} to allChannels",
                        guildStructure.getGuild_id()
                );

                allChannels.add(
                        guildStructure
                );
            }

            initialized = true;

            logger.info(
                    "Guilds reinitialized"
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error(
                    "Initialization failed due to interrupted access of guildSemaphore",
                    e
            );

        } finally {

            if (acquired) {
                guildSemaphore.release();
            }
        }
    }

    /*
     * -----------------------------------------------------------------
     * Getters
     * -----------------------------------------------------------------
     */

    public TwitchClient getTwitchClient() {
        return twitchClient;
    }

    public DiscordClient getDiscordClient() {
        return discordClient;
    }

    public GatewayDiscordClient getGatewayDiscordClient() {
        return gatewayDiscordClient;
    }

    public DBHandler getDbHandler() {
        return dbHandler;
    }

    public Boolean isInitialized() {
        return initialized;
    }

    /*
     * -----------------------------------------------------------------
     * Guild structures
     * -----------------------------------------------------------------
     */

    public void addGuildStructure(
            GuildStructure guildStructure
    ) {

        boolean acquired = false;

        try {

            guildSemaphore.acquire();
            acquired = true;

            allChannels.add(
                    guildStructure
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error(
                    "Adding guild structure for guild {} failed",
                    guildStructure.getGuild_id(),
                    e
            );

        } finally {

            if (acquired) {
                guildSemaphore.release();
            }
        }
    }

    public GuildStructure provideGuildStructure(Long guildID) {
        logger.info("Looking for guild {} in allChannels ({} entries)", guildID, allChannels.size());

        boolean acquired = false;

        try {

            guildSemaphore.acquire();
            acquired = true;

            for (
                    GuildStructure guildStructure :
                    allChannels
            ) {

                if (
                        guildStructure
                                .getGuild_id()
                                .equals(guildID)
                ) {

                    return guildStructure;
                }
            }

            return null;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error(
                    "Providing guild structure for guild {} failed",
                    guildID,
                    e
            );

            return null;

        } finally {

            if (acquired) {
                guildSemaphore.release();
            }
        }
    }

    /*
     * -----------------------------------------------------------------
     * Discord messaging
     * -----------------------------------------------------------------
     */

    public void sendMessage(
            Channel channel,
            String message
    ) {

        if (!(channel instanceof TextChannel)) {

            logger.warn(
                    "Cannot send message because channel {} is not a TextChannel",
                    channel
            );

            return;
        }

        TextChannel textChannel =
                (TextChannel) channel;

        textChannel
                .createMessage(message)
                .subscribe(
                        ignored -> {
                        },
                        error ->
                                logger.error(
                                        "Message could not be sent to channel {}",
                                        textChannel.getName(),
                                        error
                                )
                );
    }

    /**
     * Sends an embed.
     *
     * The Consumer operates on the current Discord4J
     * EmbedCreateSpec.Builder API.
     */
    public void sendMessage(
            Channel channel,
            Consumer<EmbedCreateSpec.Builder> embedBuilderConsumer
    ) {

        if (!(channel instanceof TextChannel)) {

            logger.warn(
                    "Cannot send embedded message because channel {} is not a TextChannel",
                    channel
            );

            return;
        }

        TextChannel textChannel =
                (TextChannel) channel;

        EmbedCreateSpec.Builder builder =
                EmbedCreateSpec.builder();

        embedBuilderConsumer.accept(
                builder
        );

        EmbedCreateSpec embed =
                builder.build();

        textChannel
                .createMessage(embed)
                .subscribe(
                        ignored -> {
                        },
                        error ->
                                logger.error(
                                        "Embedded message could not be sent to channel {}",
                                        textChannel.getName(),
                                        error
                                )
                );
    }

    public Long sendMessageID(
            Channel channel,
            String message
    ) {

        if (!(channel instanceof TextChannel)) {

            logger.warn(
                    "Cannot send message because channel {} is not a TextChannel",
                    channel
            );

            return null;
        }

        TextChannel textChannel =
                (TextChannel) channel;

        try {

            return textChannel
                    .createMessage(message)
                    .block()
                    .getId()
                    .asLong();

        } catch (Exception e) {

            logger.error(
                    "Message could not be sent to channel {}",
                    textChannel.getName(),
                    e
            );

            return null;
        }
    }

    public Long sendMessageID(
            Channel channel,
            Consumer<LegacyEmbedCreateSpec> embedConsumer
    ) {
        if (!(channel instanceof TextChannel)) {
            logger.warn(
                    "Cannot send embedded message because channel {} is not a TextChannel",
                    channel
            );
            return null;
        }

        TextChannel textChannel =
                (TextChannel) channel;

        try {
            return textChannel
                    .createMessage(
                            spec -> spec.addEmbed(embedConsumer)
                    )
                    .block()
                    .getId()
                    .asLong();

        } catch (Exception e) {
            logger.error(
                    "Embedded message could not be sent to channel {}",
                    textChannel.getName(),
                    e
            );

            return null;
        }
    }

    /**
     * Edits the first embed of a message.
     *
     * NOTE:
     * This method uses the modern message-edit API.
     */
    public void editMessage(
            Channel channel,
            Long messageID,
            Consumer<LegacyEmbedCreateSpec> embedConsumer
    ) {
        if (!(channel instanceof TextChannel)) {
            return;
        }

        TextChannel textChannel = (TextChannel) channel;

        try {
            textChannel
                    .getMessageById(Snowflake.of(messageID))
                    .flatMap(message ->
                            message.edit(spec ->
                                    spec.addEmbed(embedConsumer)
                            )
                    )
                    .subscribe(
                            ignored -> {
                            },
                            error -> logger.error(
                                    "Message {} could not be edited",
                                    messageID,
                                    error
                            )
                    );

        } catch (Exception e) {
            logger.error(
                    "Message {} could not be edited",
                    messageID,
                    e
            );
        }
    }

    public void editMessage(
            Channel channel,
            Long messageID,
            String message
    ) {

        if (!(channel instanceof TextChannel)) {
            return;
        }

        TextChannel textChannel =
                (TextChannel) channel;

        try {

            textChannel
                    .getMessageById(
                            Snowflake.of(messageID)
                    )
                    .flatMap(
                            existingMessage ->
                                    existingMessage.edit(
                                            spec ->
                                                    spec.setContent(
                                                            message
                                                    )
                                    )
                    )
                    .subscribe(
                            ignored -> {
                            },
                            error ->
                                    logger.error(
                                            "Message {} could not be edited",
                                            messageID,
                                            error
                                    )
                    );

        } catch (Exception e) {

            logger.error(
                    "Message {} could not be edited",
                    messageID,
                    e
            );
        }
    }

    public void deleteMessage(
            Channel channel,
            Long messageID
    ) {

        if (!(channel instanceof TextChannel)) {
            return;
        }

        TextChannel textChannel =
                (TextChannel) channel;

        try {

            textChannel
                    .getMessageById(
                            Snowflake.of(messageID)
                    )
                    .flatMap(
                            message ->
                                    message.delete()
                    )
                    .subscribe(
                            ignored -> {
                            },
                            error ->
                                    logger.error(
                                            "Message {} could not be deleted",
                                            messageID,
                                            error
                                    )
                    );

        } catch (Exception e) {

            logger.error(
                    "Message {} could not be deleted",
                    messageID,
                    e
            );
        }
    }

    /*
     * -----------------------------------------------------------------
     * Guild removal
     * -----------------------------------------------------------------
     */

    public void removeGuildStructure(
            Long guildID
    ) {

        boolean acquired = false;

        try {

            guildSemaphore.acquire();
            acquired = true;

            removeGuildStructureInternal(
                    guildID
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error(
                    "Removing guild structure for guild {} failed",
                    guildID,
                    e
            );

        } finally {

            if (acquired) {
                guildSemaphore.release();
            }
        }
    }

    /**
     * Must only be called while guildSemaphore is held.
     */
    private void removeGuildStructureInternal(
            Long guildID
    ) {

        GuildStructure guildStructure = null;

        for (
                GuildStructure g :
                allChannels
        ) {

            if (
                    g.getGuild_id()
                            .equals(guildID)
            ) {

                guildStructure = g;
                break;
            }
        }

        if (guildStructure == null) {
            return;
        }

        if (
                guildStructure.getAnnouncer() != null
        ) {

            guildStructure
                    .getAnnouncer()
                    .shutdownScheduler();
        }

        guildStructure.setAnnouncer(null);
        guildStructure.setTwitch_channels(null);
        guildStructure.setGame_filters(null);
        guildStructure.setLembot(null);

        allChannels.remove(
                guildStructure
        );
    }

    /*
     * -----------------------------------------------------------------
     * Twitch / Helix
     * -----------------------------------------------------------------
     */

    public UserList getUsers(
            List<Long> channelIDs,
            List<String> channelNames
    ) throws Exception {

        /*
         * Twitch4J 1.27:
         * user IDs are strings, not Longs.
         */
        List<String> userIds = null;

        if (channelIDs != null) {

            userIds =
                    channelIDs.stream()
                            .map(String::valueOf)
                            .toList();
        }

        return twitchClient
                .getHelix()
                .getUsers(
                        properties.getProperty(
                                "twitch_oauth"
                        ),
                        userIds,
                        channelNames
                )
                .execute();
    }

    public StreamList getStreams(
            List<String> gameIDs,
            List<Long> channelIDs
    ) throws Exception {

        /*
         * Current Twitch Helix signature:
         *
         * getStreams(
         *     authToken,
         *     after,
         *     before,
         *     limit,
         *     gameIds,
         *     language,
         *     userIds,
         *     userLogins
         * )
         *
         * Twitch user IDs are strings.
         */
        List<String> userIds = null;

        if (channelIDs != null) {

            userIds =
                    channelIDs.stream()
                            .map(String::valueOf)
                            .toList();
        }

        return twitchClient
                .getHelix()
                .getStreams(
                        properties.getProperty(
                                "twitch_oauth"
                        ),
                        "",
                        "",
                        null,
                        gameIDs,
                        null,
                        userIds,
                        null
                )
                .execute();
    }

    public GameList getGames(
            List<String> gameIDs,
            List<String> gameNames
    ) throws Exception {

        return twitchClient
                .getHelix()
                .getGames(
                        properties.getProperty(
                                "twitch_oauth"
                        ),
                        gameIDs,
                        gameNames
                )
                .execute();
    }

    /*
     * -----------------------------------------------------------------
     * Shutdown / restart
     * -----------------------------------------------------------------
     */

    public void announceOfftime() {

        boolean guildAcquired = false;

        try {

            guildSemaphore.acquire();
            guildAcquired = true;

            for (
                    GuildStructure guild :
                    allChannels
            ) {

                if (guild.getAnnouncer() == null) {
                    continue;
                }

                boolean streamAcquired = false;

                try {

                    guild.getAnnouncer()
                            .getStreamSemaphore()
                            .acquire();

                    streamAcquired = true;

                    if (
                            gatewayDiscordClient != null &&
                                    guild.getAnnounce_channel() != null
                    ) {

                        Channel channel =
                                gatewayDiscordClient
                                        .getChannelById(
                                                Snowflake.of(
                                                        guild.getAnnounce_channel()
                                                )
                                        )
                                        .block();

                        if (channel instanceof TextChannel) {

                            ((TextChannel) channel)
                                    .createMessage(
                                            "The bot is going offline for a while to push an update, fix or similar."
                                    )
                                    .block();
                        }
                    }

                    guild.getAnnouncer()
                            .shutdownScheduler();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    logger.error(
                            "Shutting down scheduler of guild {} failed",
                            guild.getGuild_id(),
                            e
                    );

                } catch (Exception e) {

                    logger.error(
                            "Offtime handling for guild {} failed",
                            guild.getGuild_id(),
                            e
                    );

                } finally {

                    if (streamAcquired) {

                        guild.getAnnouncer()
                                .getStreamSemaphore()
                                .release();
                    }
                }
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error(
                    "Announcing offtime failed",
                    e
            );

        } finally {

            if (guildAcquired) {
                guildSemaphore.release();
            }
        }
    }

    public void forceShutdown() {

        for (
                GuildStructure guild :
                allChannels
        ) {

            if (guild.getAnnouncer() != null) {

                guild.getAnnouncer()
                        .shutdownScheduler();
            }
        }

        /*
         * Do NOT replace guildSemaphore here.
         */
    }

    public void restartAfterOutage() {

        boolean acquired = false;

        try {

            guildSemaphore.acquire();
            acquired = true;

            for (
                    GuildStructure guild :
                    allChannels
            ) {

                if (guild.getAnnouncer() != null) {

                    guild.getAnnouncer()
                            .restartScheduler();
                }
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            logger.error(
                    "Restarting after outage failed",
                    e
            );

        } finally {

            if (acquired) {
                guildSemaphore.release();
            }
        }
    }

    public Logger getLogger() {
        return logger;
    }
}

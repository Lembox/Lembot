package commands;

import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.Game;

import core.DBHandler;
import core.Lembot;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.emoji.Emoji;
import discord4j.common.util.Snowflake;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import discord4j.core.spec.EmbedCreateSpec;

import models.ChannelDels;
import models.GuildStructure;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Commander {
    // private static final String discordIcon = "https://abload.de/img/dsicordlogowzcf7.png";
    private static final String discordIcon = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/discord.png";
    private static final String twitchIcon = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/twitch.png";
    // private static final String twitchIcon = "https://abload.de/img/twitchicono8dj7.png";
    private static final String githubLink = "https://github.com/lembox/lembot";

    private final Lembot lembot;
    private final DBHandler dbHandler;
    private final Logger logger;
    private TwitchHelix helix;

    public Commander(Lembot lembot) {
        this.lembot = lembot;
        dbHandler = lembot.getDbHandler();
        logger = lembot.getLogger();
    }

    public void processCommand(Message message, String prefix) {
        boolean reactionFlag = true;

        User sender = message.getAuthor().get();
        Channel channel = message.getChannel().block();
        Guild guild = message.getGuild().block();
        helix = lembot.getTwitchClient().getHelix();

        GuildStructure guildStructure = lembot.provideGuildStructure(guild.getId().asLong());

        String[] command = message.getContent().replaceFirst("^" + java.util.regex.Pattern.quote(prefix), "").split(" ", 2);

        String commandName = command[0].toLowerCase();
        String arguments = command.length > 1 ? command[1].trim() : "";

        if (dbHandler.isMaintainer(message)) {
            switch (commandName) {
                case "init":
                    lembot.sendMessage(channel, "In which channel should I announce the streams? Use the following command '!set_announce #channel'. On top of that use '!set_message classic/embedded' to set your favorite announcing format. Afterwards you're ready to set up Twitch channels and game filters, refer to !help or !commands for help.");
                    break;
                case "set_announce":
                    arguments = arguments.replace("<#", "").replace(">", "");

                    try {
                        Long channelID = Long.parseLong(arguments);
                        Channel targetChannel = lembot.getGatewayDiscordClient().getChannelById(Snowflake.of(channelID)).block();

                        if (!(targetChannel instanceof TextChannel)) {
                            lembot.sendMessage(channel, "The provided channel is not a valid text channel (it might be a category or voice channel).");
                            break;
                        }

                        TextChannel announce_channel2 = (TextChannel) targetChannel;
                        dbHandler.setAnnounceChannel(guild.getId().asLong(), channelID);
                        lembot.sendMessage(channel, "The announcement channel has been set to: " + announce_channel2.getName());

                        try {
                            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
                            guildStructure.setAnnounce_channel(channelID);
                        } catch (InterruptedException e) {
                            logger.error("Interrupted while setting announce channel", e);
                        } finally {
                            guildStructure.getAnnouncer().getStreamSemaphore().release();
                        }

                    } catch (NumberFormatException e) {
                        lembot.sendMessage(channel, "The provided channelID is not an integer.");
                    } catch (ClientException de) {
                        lembot.sendMessage(channel, "The provided channelID could not be found.");
                    }
                    break;
                case "set_message":
                    if (arguments.equalsIgnoreCase("classic") || arguments.equals("0")) {
                        lembot.sendMessage(channel, "The message style has been set to: classic.");
                        updateMessageStyle(guildStructure, 0);
                    } else if (arguments.equalsIgnoreCase("embedded") || arguments.equals("1")) {
                        lembot.sendMessage(channel, "The message style has been set to: embedded.");
                        updateMessageStyle(guildStructure, 1);
                    } else {
                        lembot.sendMessage(channel, "The possible message styles are \n0: classic \n1: embedded");
                    }
                    break;
                case "config":
                    lembot.sendMessage(channel, (EmbedCreateSpec.Builder embedCreateSpec) -> {
                        embedCreateSpec.author("Lembot configuration for " + guild.getName(), githubLink, discordIcon);

                        embedCreateSpec.addField("Announcement channel", "<#" + guildStructure.getAnnounce_channel() + ">", true);

                        if (guildStructure.getCleanup()) {
                            embedCreateSpec.addField("Message cleanup", "activated", true);
                        } else {
                            embedCreateSpec.addField("Message cleanup", "deactivated", true);
                        }

                        if (guildStructure.getMessage_style().equals(0)) {
                            embedCreateSpec.addField("Message style", "classic", true);
                        } else {
                            embedCreateSpec.addField("Message style", "embedded", true);
                        }

                        embedCreateSpec.color(Color.of(114, 137, 218));
                    });
                    break;
                case "maintainer_add":
                    if (sender.getId().asLong() == guild.getOwner().block().getId().asLong()) {
                        // in case it's of the form @userName - otherwise nothing happens
                        arguments = arguments.replace("<@", "");
                        arguments = arguments.replace(">", "");

                        try {
                            dbHandler.addMaintainerForGuild(message, Long.parseLong(arguments));
                            lembot.sendMessage(channel, "<@" + arguments + "> has been added as maintainer");
                        } catch (NumberFormatException ne) {
                            lembot.sendMessage(channel, "The user_id is not valid");
                        }
                    }
                    break;
                case "maintainer_remove":
                    if (sender.getId().asLong() == guild.getOwnerId().asLong()) {
                        // in case it's of the form @userName - otherwise nothing happens
                        arguments = arguments.replace("<@", "");
                        arguments = arguments.replace(">", "");

                        try {
                            Long maintainerID = Long.parseLong(arguments);
                            if (maintainerID.equals(guild.getOwnerId().asLong())) {
                                lembot.sendMessage(channel, "The owner cannot be removed");
                            } else {
                                dbHandler.deleteMaintainerForGuild(message, Long.parseLong(arguments));
                                lembot.sendMessage(channel, "<@" + arguments + "> has been removed as maintainer");
                            }
                        } catch (NumberFormatException ne) {
                            lembot.sendMessage(channel, "The user_id is not valid");
                        }
                    }
                    break;
                case "maintainers":
                    List<Long> maintainers = dbHandler.getMaintainers(guild.getId().asLong());
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("The maintainers of the bot in this guild are: \n");

                    for (Long l : maintainers) {
                        stringBuilder.append(lembot.getGatewayDiscordClient().getUserById(Snowflake.of(l)).block().getUsername()).append("\n");
                    }

                    lembot.sendMessage(channel, stringBuilder.toString());
                    break;
                case "dm_guild_owner": {
                    // Usage: !dm_guild_owner <guild_id> <message>
                    if (!isBotOwner(sender)) {
                        lembot.sendMessage(channel, "⛔ This command is restricted to the bot owner.");
                        break;
                    }

                    String[] parts = arguments.split(" ", 2);
                    if (parts.length < 2) {
                        lembot.sendMessage(channel, "Usage: `!dm_guild_owner <guild_id> <message>`");
                        break;
                    }

                    try {
                        Long targetGuildId = Long.parseLong(parts[0].trim());
                        String textToSend = parts[1].trim();

                        lembot.getGatewayDiscordClient().getGuildById(Snowflake.of(targetGuildId))
                                .flatMap(g -> g.getOwner()
                                        .flatMap(owner -> owner.getPrivateChannel())
                                        .flatMap(dm -> dm.createMessage("📩 **Message from the Lembot Developer:**\n\n" + textToSend))
                                )
                                .subscribe(
                                        success -> lembot.sendMessage(channel, "✅ Successfully sent DM to the owner of guild `" + targetGuildId + "`!"),
                                        err -> lembot.sendMessage(channel, "❌ Failed to send DM: " + err.getMessage())
                                );
                    } catch (NumberFormatException e) {
                        lembot.sendMessage(channel, "Invalid Guild ID.");
                    }
                    break;
                }
                case "presence":
                case "set_presence": {
                    // 1. Authenticate owner
                    if (!isBotOwner(sender)) {
                        lembot.sendMessage(channel, "⛔ This command is restricted to the bot owner.");
                        break;
                    }

                    // 2. Validate arguments
                    if (arguments.isBlank()) {
                        lembot.sendMessage(channel, "ℹ️ **Usage:**\n" +
                                "`!presence playing <text>`\n" +
                                "`!presence watching <text>`\n" +
                                "`!presence listening <text>`\n" +
                                "`!presence competing <text>`\n" +
                                "`!presence streaming <title>`\n" +
                                "`!presence streaming <twitch_url> <title>`\n" +
                                "`!presence clear`");
                        break;
                    }

                    String[] parts = arguments.split(" ", 2);
                    String type = parts[0].toLowerCase();
                    String content = parts.length > 1 ? parts[1].trim() : "";

                    ClientActivity activity = null;

                    switch (type) {
                        case "playing":
                            if (content.isBlank()) {
                                lembot.sendMessage(channel, "Please specify what you are playing. E.g.: `!presence playing !commands`");
                                break;
                            }
                            activity = ClientActivity.playing(content);
                            break;

                        case "watching":
                            if (content.isBlank()) {
                                lembot.sendMessage(channel, "Please specify what you are watching. E.g.: `!presence watching Twitch streams`");
                                break;
                            }
                            activity = ClientActivity.watching(content);
                            break;

                        case "listening":
                            if (content.isBlank()) {
                                lembot.sendMessage(channel, "Please specify what you are listening to. E.g.: `!presence listening to feedback`");
                                break;
                            }
                            activity = ClientActivity.listening(content);
                            break;

                        case "competing":
                            if (content.isBlank()) {
                                lembot.sendMessage(channel, "Please specify the tournament. E.g.: `!presence competing in Speedruns`");
                                break;
                            }
                            activity = ClientActivity.competing(content);
                            break;

                        case "streaming":
                            if (content.isBlank()) {
                                lembot.sendMessage(channel, "Usage: `!presence streaming <title>` or `!presence streaming <url> <title>`");
                                break;
                            }
                            String streamUrl = "https://twitch.tv";
                            String streamTitle = content;

                            // Check if user passed an explicit stream URL first
                            String[] streamArgs = content.split(" ", 2);
                            if (streamArgs[0].startsWith("http://") || streamArgs[0].startsWith("https://")) {
                                streamUrl = streamArgs[0];
                                streamTitle = streamArgs.length > 1 ? streamArgs[1] : "Live Stream";
                            }
                            activity = ClientActivity.streaming(streamTitle, streamUrl);
                            break;

                        case "clear":
                        case "reset":
                            lembot.getGatewayDiscordClient().updatePresence(ClientPresence.online()).subscribe(
                                    success -> lembot.sendMessage(channel, "✅ Presence cleared."),
                                    error -> lembot.sendMessage(channel, "❌ Failed to clear presence: " + error.getMessage())
                            );
                            break;

                        default:
                            lembot.sendMessage(channel, "Unknown type `" + type + "`. Options: `playing`, `watching`, `listening`, `competing`, `streaming`, `clear`.");
                            break;
                    }

                    if (activity != null) {
                        final String finalContent = content;
                        final String finalType = type;
                        lembot.getGatewayDiscordClient().updatePresence(ClientPresence.online(activity)).subscribe(
                                success -> lembot.sendMessage(channel, "✅ Bot presence updated to: **" + finalType.toUpperCase() + "** " + finalContent),
                                error -> lembot.sendMessage(channel, "❌ Failed to update presence: " + error.getMessage())
                        );
                    }
                    break;
                }
                case "game_add":
                    try {
                        if (!arguments.equals("")) {
                            if (arguments.contains("|")) {
                                String[] games = arguments.split("\\|");
                                add_games(games, guildStructure, channel, message);
                            } else {
                                add_games(new String[]{arguments}, guildStructure, channel, message);
                            }
                        } else {
                            lembot.sendMessage(channel, "Use the command properly");
                        }
                    } catch (Exception e) {
                        lembot.sendMessage(channel, "An error occured.");
                        logger.error("Error occured in game_add", e);
                    }

                    break;
                case "game_remove":
                    if (!arguments.equals("")) {
                        if (arguments.contains("|")) {
                            String[] games = arguments.split("\\|");
                            StringBuilder gameRem = new StringBuilder();

                            for (String game : games) {
                                if (!game.equals("")) {
                                    if (gameRem.length() > 1500) {
                                        lembot.sendMessage(channel, gameRem.toString());
                                        gameRem = new StringBuilder();
                                    }
                                    gameRem.append(remove_game(game, guildStructure, message)).append("\n");
                                }
                            }

                            lembot.sendMessage(channel, gameRem.toString());
                        } else {
                            lembot.sendMessage(channel, remove_game(arguments, guildStructure, message));
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "game_filters":
                    Integer gameParts = 1;     // message splits
                    StringBuilder gameNames = new StringBuilder();
                    StringBuilder gameIDs = new StringBuilder();

                    List<String> gameFilters = dbHandler.getGamesForGuild(guild.getId().asLong());
                    Map<String, String> gameMap = new LinkedHashMap<>();

                    if (gameFilters.isEmpty()) {
                        lembot.sendMessage(channel, "No game filters have been set up yet");
                    } else {
                        for (String game : gameFilters) {
                            gameMap.put(game, null);
                        }
                        try {
                            List<Game> games = lembot.getGames(null, gameFilters);

                            for (Game g : games) {
                                gameMap.put(g.getName(), g.getId());
                            }

                            String gameUrl = null;

                            Iterator<Map.Entry<String, String>> it = gameMap.entrySet().iterator();
                            while (it.hasNext()) {
                                Map.Entry<String, String> pair = it.next();
                                try {
                                    gameUrl = "https://twitch.tv/directory/game/" + URLEncoder.encode(pair.getKey(), "UTF-8").replaceAll("\\+", "%20");
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }

                                gameNames.append("[").append(pair.getKey()).append("](").append(gameUrl).append(")\n");
                                gameIDs.append(pair.getValue()).append("\n");

                                if (gameNames.length() + gameIDs.length() > 900) {
                                    gameParts++;
                                    lembot.sendMessage(channel, buildGameList(gameNames.toString(), gameIDs.toString(), guild.getName(), gameParts));
                                    gameNames = new StringBuilder();
                                    gameIDs = new StringBuilder();
                                }
                            }

                            if (gameParts > 1) {
                                gameParts++;
                            }
                            lembot.sendMessage(channel, buildGameList(gameNames.toString(), gameIDs.toString(), guild.getName(), gameParts));
                        } catch (Exception e) {
                            logger.error("Displaying game list failed", e);
                        }
                    }
                    break;
                case "twitch_add":
                    if (!arguments.equals("")) {
                        if (arguments.contains("|")) {
                            String[] channels = arguments.split("\\|");
                            add_channels(channels, guildStructure, channel, message);
                        } else {
                            add_channels(new String[]{arguments}, guildStructure, channel, message);
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "twitch_remove":
                    if (!arguments.equals("")) {
                        if (arguments.contains("|")) {
                            StringBuilder channelRem = new StringBuilder();
                            String[] channels = arguments.split("\\|");

                            for (String s : channels) {
                                if (!s.equals("")) {
                                    if (channelRem.length() > 1500) {
                                        lembot.sendMessage(channel, channelRem.toString());
                                        channelRem = new StringBuilder();
                                    }
                                    channelRem.append(remove_channel(s, guildStructure, message)).append("\n");
                                }
                            }

                            lembot.sendMessage(channel, channelRem.toString());
                        } else {
                            lembot.sendMessage(channel, remove_channel(arguments, guildStructure, message));
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "twitch_channels":
                    Integer channelParts = 1;
                    StringBuilder channelNames = new StringBuilder();
                    StringBuilder channelIDs = new StringBuilder();

                    List<ChannelDels> twitch_channels = dbHandler.getChannelsForGuild(guild.getId().asLong());

                    if (twitch_channels.isEmpty()) {
                        lembot.sendMessage(channel, "No channels have been added yet.");
                    } else {
                        for (ChannelDels cd : twitch_channels) {
                            if (channelNames.length() + channelIDs.length() > 900) {
                                channelParts++;
                                lembot.sendMessage(channel, buildChannelList(channelNames.toString(), channelIDs.toString(), guild.getName(), channelParts));
                                channelNames = new StringBuilder();
                                channelIDs = new StringBuilder();
                            }

                            channelNames.append("[").append(cd.getName()).append("](https://twitch.tv/").append(cd.getName()).append(")\n");
                            channelIDs.append(cd.getChannelID()).append("\n");
                        }
                        if (channelParts > 1) {
                            channelParts++;
                        }
                        lembot.sendMessage(channel, buildChannelList(channelNames.toString(), channelIDs.toString(), guild.getName(), channelParts));
                    }
                    break;
                case "cleanup":
                    Boolean result = dbHandler.updateCleanup(guild.getId().asLong());
                    try {
                        guildStructure.getAnnouncer().getStreamSemaphore().acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    guildStructure.setCleanup(result);

                    if (result) {
                        lembot.sendMessage(channel, "Message cleanup has been activated.");
                    } else {
                        lembot.sendMessage(channel, "Message cleanup has been deactivated.");
                    }
                    guildStructure.getAnnouncer().getStreamSemaphore().release();
                    break;
                case "restart":
                    guildStructure.getAnnouncer().restartScheduler();
                    lembot.sendMessage(channel, "The stream announcer will be restarted");
                    break;
                case "shutdown":
                    guildStructure.getAnnouncer().shutdownScheduler();
                    lembot.sendMessage(channel, "The stream announcer has been shutdown");
                    break;
                case "raw_data":        // outputs filtered channels and games to easily copy them -> bot change or similar
                    List<String> raw_games = dbHandler.getGamesForGuild(guild.getId().asLong());
                    List<ChannelDels> raw_channels = dbHandler.getChannelsForGuild(guild.getId().asLong());

                    StringBuilder sb_games = new StringBuilder();
                    StringBuilder sb_channels = new StringBuilder();

                    for (String s : raw_games) {
                        if (sb_games.length() > 1500) {
                            lembot.sendMessage(channel, "games: " + sb_games.toString());
                            sb_games = new StringBuilder();
                        }

                        sb_games.append(s).append("|");
                    }
                    for (ChannelDels cd : raw_channels) {
                        if (sb_channels.length() > 1500) {
                            lembot.sendMessage(channel, "channels: " + sb_channels.toString());
                            sb_channels = new StringBuilder();
                        }
                        sb_channels.append(cd.getName()).append("|");
                    }

                    lembot.sendMessage(channel, "games: " + sb_games.toString() + "\nchannels: " + sb_channels.toString());
                    break;
                case "help":
                case "commands":
                    lembot.sendMessage(channel, "https://github.com/Lembox/lembot"); // till tables are possible for Discord messages :/
                    break;
                case "status":
                    lembot.sendMessage(channel, "Lembot is online announcing selected Twitch channels streaming selected games, last update: 10.07.2019");
                    break;
                default:
                    reactionFlag = false;
                    break;
            }
            if (reactionFlag) {
                logger.debug("Command {} is used by {} in guild {} (id: {})", command[0], sender.getUsername(), guild.getName(), guild.getId().asLong());

                try {
                    Emoji reaction = Emoji.unicode("\uD83D\uDC4C");
                    message.addReaction(reaction).subscribe();
                } catch (ClientException mpe) {
                    logger.error("Reaction to command {} used in guild {} (id: {}) couldn't be added", command[0], guild.getName(), guild.getId().asLong(), mpe);
                }
            }
        }
    }

    private void add_channels(String[] channelNames, GuildStructure guildStructure, Channel channel, Message message) {
        StringBuilder response = new StringBuilder();

        List<String> newChannelNames = new ArrayList<>();
        List<Long> newChannelIDs = new ArrayList<>();

        boolean acquired = false;
        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
            acquired = true;

            for (String c : channelNames) {
                Long channelID = null;
                ChannelDels cd;
                if (StringUtils.isNumeric(c)) {
                    cd = findChannelDelsWithIDAlreadyThere(guildStructure, Long.parseLong(c));
                    channelID = Long.parseLong(c);
                } else {
                    cd = findChannelDelsWithIDAlreadyThere(guildStructure, c);
                }

                if (cd != null) {
                    response.append("Channel: ").append(cd.getName()).append(" has already been added\n");
                } else {
                    if (channelID != null) {
                        newChannelIDs.add(channelID);
                    } else {
                        newChannelNames.add(c);
                    }
                }

                if (response.length() > 1500) {
                    lembot.sendMessage(channel, response.toString());
                    response = new StringBuilder();
                }
            }

            if (!newChannelIDs.isEmpty() || !newChannelNames.isEmpty()) {
                try {
                    List<com.github.twitch4j.helix.domain.User> users = lembot.getUsers(newChannelIDs, newChannelNames);

                    for (com.github.twitch4j.helix.domain.User u : users) {
                        newChannelNames.removeIf(name -> name.equalsIgnoreCase(u.getDisplayName()));
                        newChannelIDs.remove(Long.valueOf(u.getId())); // FIXED
                        dbHandler.addChannelForGuild(message, Long.parseLong(u.getId()), u.getDisplayName());
                        response.append("Channel ").append(u.getDisplayName()).append(" with ID: ").append(u.getId()).append(" will be added\n");

                        guildStructure.addChannel(u);

                        if (response.length() > 1500) {
                            lembot.sendMessage(channel, response.toString());
                            response = new StringBuilder();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Adding channels failed in guild {}", guildStructure.getGuild_id(), e);
                    lembot.sendMessage(channel, "Something went wrong, did you provide a non-valid channel name/ID like \" \", or a symbol?");
                }
            }

        } catch (InterruptedException e) {
            logger.error("Interrupted access of streamSemaphore during the addition of channels for guild {}", guildStructure.getGuild_id(), e);
        } finally {
            if (acquired) {
                guildStructure.getAnnouncer().getStreamSemaphore().release();
            }
        }

        for (String n : newChannelNames) {
            response.append("Channel: ").append(n).append(" cannot be found.\n");
            if (response.length() > 1500) {
                lembot.sendMessage(channel, response.toString());
                response = new StringBuilder();
            }
        }

        for (Long l : newChannelIDs) {
            response.append("Channel with ID: ").append(l).append(" cannot be found.\n");
            if (response.length() > 1500) {
                lembot.sendMessage(channel, response.toString());
                response = new StringBuilder();
            }
        }

        lembot.sendMessage(channel, response.toString());
    }

    private String remove_channel(String channel, GuildStructure guildStructure, Message message) {
        Semaphore semaphore = guildStructure.getAnnouncer().getStreamSemaphore();

        if (StringUtils.isNumeric(channel)) {
            // assuming input is ChannelID
            try {
                ChannelDels cd = findChannelDelsWithIDAlreadyThere(guildStructure, Long.parseLong(channel));
                if (cd != null) {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    guildStructure.removeChannel(cd);
                    semaphore.release();
                    dbHandler.deleteChannelForGuild(message.getGuild().block(), cd.getChannelID());

                    return ("Channel " + cd.getName() + "with ID: " + cd.getChannelID() + " will be removed");
                } else {
                    return ("Channel with the ID: " + channel + " has already been removed or has never been added");
                }
            } catch (Exception e) {
                return ("Something went wrong with " + channel);
            }
        } else {
            // assuming input is ChannelName
            try {
                ChannelDels cd = findChannelDelsWithIDAlreadyThere(guildStructure, channel.toLowerCase());
                if (cd != null) {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    guildStructure.removeChannel(cd);
                    semaphore.release();
                    dbHandler.deleteChannelForGuild(message.getGuild().block(), cd.getChannelID());
                    return ("Channel " + cd.getName() + " with ID: " + cd.getChannelID() + " will be removed");
                } else {
                    return ("Channel with name: " + channel + " has already been removed");
                }
            } catch (Exception e) {
                return ("Something went wrong with " + channel);
            }
        }
    }

    private void add_games(String[] games, GuildStructure guildStructure, Channel channel, Message message) {
        StringBuilder response = new StringBuilder();

        List<String> newGames = new ArrayList<>();

        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            logger.error("Interrupted access of streamSemaphore during the addition of games for guild {}", guildStructure.getGuild_id(), e);
        }

        Map<String, String> gameFilterz = guildStructure.getGame_filters().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (existing, replacing) -> existing));

        for (String g : games) {
            String key = gameFilterz.get(g);

            if (key != null) {
                response.append("A filter for ").append(g).append(" has already been set up\n");
            } else {
                newGames.add(g);
            }

            if (response.length() > 1500) {
                lembot.sendMessage(channel, response.toString());
                response = new StringBuilder();
            }
        }

        try {
            List<Game> gamez = lembot.getGames(null, newGames);

            for (Game g : gamez) {
                newGames.removeIf(name -> name.equalsIgnoreCase(g.getName()));
                dbHandler.addGameForGuild(message.getGuild().block(), g.getName());
                response.append("A filter for ").append(g.getName()).append(" will be added\n");
                guildStructure.addGameFilter(g.getId(), g.getName());

                if (response.length() > 1500) {
                    lembot.sendMessage(channel, response.toString());
                    response = new StringBuilder();
                }
            }

            for (String g : newGames) {
                response.append("Beware that a directory for the game ").append(g).append(" hasn't been added on Twitch yet and therefore it cannot be detected. See here for more information on how to add it: https://help.twitch.tv/customer/en/portal/articles/2348988-adding-a-game-and-box-art-to-the-directory");

                if (response.length() > 1500) {
                    lembot.sendMessage(channel, response.toString());
                    response = new StringBuilder();
                }
            }
        } catch (Exception e) {
            logger.error("Adding games failed in guild {}", guildStructure.getGuild_id(), e);
            lembot.sendMessage(channel, "Something went wrong");
        } finally {
            guildStructure.getAnnouncer().getStreamSemaphore().release();
        }


        lembot.sendMessage(channel, response.toString());
    }

    private String remove_game(String game, GuildStructure guildStructure, Message message) {
        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (game == null) {
            return ("An error occured");
        } else {
            String some_game = game.toLowerCase();
            String key = guildStructure.getGame_filters().entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(some_game)).map(Map.Entry::getKey).findFirst().orElse(null);

            System.out.println(Arrays.toString(guildStructure.getGame_filters().entrySet().toArray()));

            if (key == null) {
                guildStructure.getAnnouncer().getStreamSemaphore().release();
                return ("A filter for " + game + " hasn't been set up");
            } else {
                dbHandler.deleteGameForGuild(message.getGuild().block(), game);
                guildStructure.removeGameFilter(key);
                guildStructure.getAnnouncer().getStreamSemaphore().release();
                return ("The filter for " + game + " will be removed");
            }
        }
    }

    private ChannelDels findChannelDelsWithIDAlreadyThere(GuildStructure guildStructure, Long channelID) {
        List<ChannelDels> twitch_channels = guildStructure.getTwitch_channels();

        for (ChannelDels cd : twitch_channels) {
            if (cd.getChannelID().equals(channelID)) {
                return cd;
            }
        }
        return null;
    }

    private ChannelDels findChannelDelsWithIDAlreadyThere(GuildStructure guildStructure, String channelName) {
        List<ChannelDels> twitch_channels = guildStructure.getTwitch_channels();

        for (ChannelDels cd : twitch_channels) {
            if (cd.getName().toLowerCase().equals(channelName.toLowerCase())) {
                return cd;
            }
        }
        return null;
    }

    private void updateMessageStyle(GuildStructure guildStructure, Integer value) {
        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        lembot.provideGuildStructure(guildStructure.getGuild_id()).setMessage_style(value);
        guildStructure.getAnnouncer().getStreamSemaphore().release();

        dbHandler.changeMessageStyle(guildStructure.getGuild_id(), value);
    }

    private String getGameID(String game) {
        List<String> games = new ArrayList<>(Collections.singletonList(game));
        try {
            List<Game> gamez = lembot.getGames(null, games);

            if (gamez.isEmpty()) {
                return null;
            } else {
                return gamez.get(0).getId();
            }
        } catch (Exception e) {
            logger.error("Getting Game IDs failed", e);
            return null;
        }

    }

    private Consumer<EmbedCreateSpec.Builder> buildGameList(String gameNames, String gameIDs, String guildName, Integer partNumber) {

        return spec -> {
            if (partNumber > 1) {
                spec.author("Game filter list (pt. " + (partNumber - 1) + ") for " + guildName, githubLink, twitchIcon);
            } else {
                spec.author("Game filter list for " + guildName, githubLink, twitchIcon);
            }

            spec.addField("Game", gameNames, true);
            spec.addField("ID", gameIDs, true);
            spec.color(Color.of(100, 65, 164));
        };
    }

    private Consumer<EmbedCreateSpec.Builder> buildChannelList(String channelNames, String channelIDs, String guildName, Integer partNumber) {

        return spec -> {
            if (partNumber > 1) {
                spec.author("Channel list (pt. " + (partNumber - 1) + ") for " + guildName, githubLink, twitchIcon);
            } else {
                spec.author("Channel list for " + guildName, githubLink, twitchIcon);
            }

            spec.addField("Channel", channelNames, true);
            spec.addField("ID", channelIDs, true);
            spec.color(Color.of(100, 65, 164));
        };
    }

    private Boolean isBotOwner(User sender) {
        Long ownerId = lembot.getBotOwnerId();
        return(ownerId != null && sender.getId().asLong() == ownerId);
    }
}

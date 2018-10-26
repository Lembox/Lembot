package commands;

import core.DBHandler;
import core.Lembot;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import me.philippheuer.twitch4j.exceptions.RestException;
import me.philippheuer.twitch4j.model.Channel;
import me.philippheuer.util.rest.HeaderRequestInterceptor;

import models.ChannelDels;
import models.Games;
import models.GuildStructure;

import org.apache.commons.lang3.StringUtils;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.springframework.web.client.RestTemplate;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RequestBuffer;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.Semaphore;

public class Commander {
    private static final String discordIcon = "https://abload.de/img/dsicordlogowzcf7.png";
    private static final String twitchIcon = "https://abload.de/img/twitchicono8dj7.png";
    private static final String githubLink = "https://github.com/lembox/lembot";

    private Lembot lembot;
    private DBHandler dbHandler;
    private Logger logger;

    public Commander(Lembot lembot) {
        this.lembot = lembot;
        dbHandler = lembot.getDbHandler();
        logger = lembot.getLogger();
    }

    public void processCommand(IMessage message, String prefix) {
        boolean reactionFlag = true;
        IUser sender = message.getAuthor();
        IChannel channel = message.getChannel();
        IGuild guild = message.getGuild();

        GuildStructure guildStructure = lembot.provideGuildStructure(guild.getLongID());

        String[] command = message.getContent().toLowerCase().replaceFirst(prefix, "").split(" ", 2);

        if (dbHandler.isMaintainer(message)) {
            switch (command[0]) {
                case "init":
                    lembot.sendMessage(channel, "In which channel should I announce the streams? Use the following command '!set_announce #channel'. On top of that use '!set_message classic/embedded' to set your favorite announcing format. Afterwards you're ready to set up Twitch channels and game filters, refer to !help or !commands for help.");
                    break;
                case "set_announce":
                    // in case it's of the form #channel - otherwise nothing happens
                    command[1] = command[1].replace("<#", "");
                    command[1] = command[1].replace(">", "");

                    try {
                        Long channelID = Long.parseLong(command[1]);
                        dbHandler.setAnnounceChannel(guild.getLongID(), channelID);
                        IChannel announce_channel = lembot.getDiscordClient().getChannelByID(channelID);
                        lembot.sendMessage(channel, "The announcement channel has been set to: " + announce_channel.toString());

                        try {
                            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        guildStructure.setAnnounce_channel(channelID);
                        guildStructure.getAnnouncer().getStreamSemaphore().release();
                    } catch (NumberFormatException e) {
                        lembot.sendMessage(channel, "The provided channelID is not an integer.");
                    } catch (DiscordException de) {
                        lembot.sendMessage(channel, "The provided channelID is not valid.");
                    }
                    break;
                case "set_message":
                    if (command[1].toLowerCase().equals("classic") || command[1].equals("0")) {
                        lembot.sendMessage(channel, "The message style has been set to: classic.");
                        updateMessageStyle(guildStructure, 0);
                    } else if (command[1].toLowerCase().equals("embedded") || command[1].equals("1")) {
                        lembot.sendMessage(channel, "The message style has been set to: embedded.");
                        updateMessageStyle(guildStructure, 1);
                    } else {
                        lembot.sendMessage(channel, "The possible message styles are \n0: classic \n1: embedded");
                    }
                    break;
                case "config":
                    EmbedBuilder builder = new EmbedBuilder();

                    builder.withAuthorName("Lembot configuration for " + guild.getName());
                    builder.withAuthorUrl(githubLink);
                    builder.withAuthorIcon(discordIcon);

                    builder.appendField("Announcement channel", "<#" + guildStructure.getAnnounce_channel() + ">", true);

                    if (guildStructure.getCleanup()) {
                        builder.appendField("Message cleanup", "activated", true);
                    }
                    else {
                        builder.appendField("Message cleanup", "deactivated", true);
                    }

                    if (guildStructure.getMessage_style().equals(0)) {
                        builder.appendField("Message style", "classic", true);
                    }
                    else {
                        builder.appendField("Message style", "embedded", true);
                    }

                    builder.withColor(114,137,218);

                    builder.withThumbnail(lembot.getDiscordClient().getApplicationIconURL());

                    lembot.sendMessage(channel, builder.build());
                    break;
                case "maintainer_add":
                    if (sender.getLongID() == guild.getOwnerLongID()) {
                        // in case it's of the form @userName - otherwise nothing happens
                        command[1] = command[1].replace("<@", "");
                        command[1] = command[1].replace(">", "");

                        try {
                            dbHandler.addMaintainerForGuild(message, Long.parseLong(command[1]));
                            lembot.sendMessage(channel, "<@" + command[1] + "> has been added as maintainer");
                        } catch (NumberFormatException ne) {
                            lembot.sendMessage(channel, "The user_id is not valid");
                        }
                    }
                    break;
                case "maintainer_remove":
                    if (sender.getLongID() == guild.getOwnerLongID()) {
                        // in case it's of the form @userName - otherwise nothing happens
                        command[1] = command[1].replace("<@", "");
                        command[1] = command[1].replace(">", "");

                        try {
                            Long maintainerID = Long.parseLong(command[1]);
                            if (maintainerID.equals(guild.getOwnerLongID())) {
                                lembot.sendMessage(channel, "The owner cannot be removed");
                            } else {
                                dbHandler.deleteMaintainerForGuild(message, Long.parseLong(command[1]));
                                lembot.sendMessage(channel, "<@" + command[1] + "> has been removed as maintainer");
                            }
                        } catch (NumberFormatException ne) {
                            lembot.sendMessage(channel, "The user_id is not valid");
                        }
                    }
                    break;
                case "maintainers":
                    List<Long> maintainers = dbHandler.getMaintainers(guild.getLongID());
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("The maintainers of the bot in this guild are: \n");

                    for (Long l : maintainers) {
                        stringBuilder.append(lembot.getDiscordClient().getUserByID(l).getName()).append("\n");
                    }

                    lembot.sendMessage(channel, stringBuilder.toString());
                    break;
                case "game_add":
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            StringBuilder gameAdd = new StringBuilder();
                            String[] games = command[1].split("\\|");

                            for (String game : games) {
                                if (!game.equals("")) {
                                    if (gameAdd.length() > 1500) {
                                        lembot.sendMessage(channel, gameAdd.toString());
                                        gameAdd = new StringBuilder();
                                    }
                                    gameAdd.append(add_game(game, guildStructure, message)).append("\n");
                                }
                            }

                            lembot.sendMessage(channel, gameAdd.toString());
                        } else {
                            lembot.sendMessage(channel, add_game(command[1], guildStructure, message));
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "game_remove":
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            String[] games = command[1].split("\\|");
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
                            lembot.sendMessage(channel, remove_game(command[1], guildStructure, message));
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "game_filters":
                    Integer gameParts = 1;     // message splits
                    StringBuilder gameNames = new StringBuilder();
                    StringBuilder gameIDs = new StringBuilder();
                    List<String> gameFilters = dbHandler.getGamesForGuild(guild.getLongID());

                    if (gameFilters.isEmpty()) {
                        lembot.sendMessage(channel, "No game filters have been set up yet");
                    }
                    else {
                        for (String game : gameFilters) {
                            if (gameNames.length() + gameIDs.length() > 900) {
                                gameParts++;
                                lembot.sendMessage(channel, buildGameList(gameNames.toString(), gameIDs.toString(), guild.getName(), gameParts));
                                gameNames = new StringBuilder();
                                gameIDs = new StringBuilder();
                            }


                            String gameUrl = null;
                            try {
                                gameUrl = "https://twitch.tv/directory/game/" + URLEncoder.encode(game, "UTF-8").replaceAll("\\+", "%20");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }

                            gameNames.append("[").append(game).append("](").append(gameUrl).append(")\n");

                            String gameID = getGameID(game);
                            if (gameID == null) {
                                gameIDs.append("no directory on Twitch").append("\n");
                            }
                            else {
                                gameIDs.append(gameID).append("\n");
                            }
                        }
                        if (gameParts > 1) {
                            gameParts++;
                        }
                        lembot.sendMessage(channel, buildGameList(gameNames.toString(), gameIDs.toString(), guild.getName(), gameParts));
                    }
                    break;
                case "twitch_add":
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            StringBuilder channelAdd = new StringBuilder();
                            String[] channels = command[1].split("\\|");

                            for (String s : channels) {
                                if (!s.equals("")) {
                                    if (channelAdd.length() > 1500) {
                                        lembot.sendMessage(channel, channelAdd.toString());
                                        channelAdd = new StringBuilder();
                                    }
                                    channelAdd.append(add_channel(s, guildStructure, message)).append("\n");
                                }
                            }

                            lembot.sendMessage(channel, channelAdd.toString());
                        } else {
                            lembot.sendMessage(channel, add_channel(command[1], guildStructure, message));
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "twitch_remove":
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            StringBuilder channelRem = new StringBuilder();
                            String[] channels = command[1].split("\\|");

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
                            lembot.sendMessage(channel, remove_channel(command[1], guildStructure, message));
                        }
                    } else {
                        lembot.sendMessage(channel, "Use the command properly");
                    }
                    break;
                case "twitch_channels":
                    Integer channelParts = 1;
                    StringBuilder channelNames = new StringBuilder();
                    StringBuilder channelIDs = new StringBuilder();

                    List<ChannelDels> twitch_channels = dbHandler.getChannelsForGuild(guild.getLongID());

                    if (twitch_channels.isEmpty()) {
                        lembot.sendMessage(channel, "No channels have been added yet.");
                    }
                    else {
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
                    Boolean result = dbHandler.updateCleanup(guild.getLongID());
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
                    List<String> raw_games = dbHandler.getGamesForGuild(guild.getLongID());
                    List<ChannelDels> raw_channels = dbHandler.getChannelsForGuild(guild.getLongID());

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
                            lembot.sendMessage(channel, "games: " + sb_channels.toString());
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
                    break;
                default:
                    reactionFlag = false;
                    break;
            }
            if (reactionFlag) {
                logger.debug("Command {} is used by {} in guild {} (id: {})", command[0], sender.getName(), guild.getName(), guild.getLongID());

                try {
                    ReactionEmoji reaction = ReactionEmoji.of("\uD83D\uDC4C");
                    RequestBuffer.request(() -> {
                        message.addReaction(reaction);
                    });
                }
                catch (MissingPermissionsException mpe) {
                    logger.error("Reaction to command {} used in guild {} (id: {}) couldn't be added", command[0], guild.getName(), guild.getLongID(), mpe);
                }
            }
        }
    }

    private String add_channel(String channel, GuildStructure guildStructure, IMessage message) {
        Semaphore semaphore = guildStructure.getAnnouncer().getStreamSemaphore();

        if(StringUtils.isNumeric(channel)) {
            // assuming input is ChannelID
            try {
                ChannelEndpoint ce = lembot.getChannelEndpoint();
                Channel c = ce.getChannel(Long.parseLong(channel));
                if (!checkIfChannelIDAlreadyThere(guildStructure, Long.parseLong(channel))) {
                    dbHandler.addChannelForGuild(message, c.getId(), c.getName());
                    try {
                        semaphore.acquire();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    guildStructure.addChannel(c);
                    semaphore.release();
                    return ("Channel " + c.getName() + " with ID: " + c.getId() + " will be added");
                }
                else {
                    return ("Channel " + c.getName() +  " has been already added");
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return ("Something went wrong");
            }
        }
        else {
            // assuming input is ChannelName
            try {
                ChannelEndpoint ce = lembot.getChannelEndpoint();
                Channel c = ce.getChannel(channel);
                if (!checkIfChannelIDAlreadyThere(guildStructure, c.getId())) {
                    dbHandler.addChannelForGuild(message, c.getId(), c.getName());
                    try {
                        semaphore.acquire();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    guildStructure.addChannel(c);
                    semaphore.release();
                    return ("Channel " + c.getName() + " with ID: " + c.getId() + " will be added");
                }
                else {
                    return ("Channel " + c.getName() +  " has already been added");
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return ("Something went wrong");
            }
        }
    }

    private String remove_channel(String channel, GuildStructure guildStructure, IMessage message) {
        Semaphore semaphore = guildStructure.getAnnouncer().getStreamSemaphore();

        if(StringUtils.isNumeric(channel)) {
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
                    dbHandler.deleteChannelForGuild(message.getGuild(), cd.getChannelID());

                    return ("Channel " + cd.getName() + "with ID: " + cd.getChannelID() + " will be removed");
                }
                else {
                    return ("Channel with the ID: " + channel + " has already been removed or has never been added");
                }
            }
            catch (Exception e) {
                return ("Something went wrong with " + channel);
            }
        }
        else {
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
                    dbHandler.deleteChannelForGuild(message.getGuild(), cd.getChannelID());
                    return ("Channel " + cd.getName() + " with ID: " + cd.getChannelID() + " will be removed");
                }
                else {
                    return ("Channel with name: " + channel + " has already been removed");
                }
            }
            catch (Exception e) {
                return ("Something went wrong with " + channel);
            }
        }
    }

    private String add_game(String game, GuildStructure guildStructure, IMessage message) {
        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            logger.error("Interrupted access of streamSemaphore during adding of game {} for guild {}", game, guildStructure.getGuild_id(), e);
        }

        List<String> gameFilters = guildStructure.getGame_filters();

        if (gameFilters.contains(game)) {
            guildStructure.getAnnouncer().getStreamSemaphore().release();
            return ("A filter for " + game + " has already been set up");
        } else {
            dbHandler.addGameForGuild(message.getGuild(), game);
            String response = "A filter for " + game + " will be added";
            guildStructure.addGameFilter(game);
            if (getGameID(game) == null) {
                response += "\nBeware that a directory for the game " + game + " hasn't been added on Twitch yet. See here for more information on how to add it: https://help.twitch.tv/customer/en/portal/articles/2348988-adding-a-game-and-box-art-to-the-directory";
            }
            guildStructure.getAnnouncer().getStreamSemaphore().release();
            return response;
        }
    }

    private String remove_game(String game, GuildStructure guildStructure, IMessage message) {
        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<String> gameFilters = guildStructure.getGame_filters();

        if (!gameFilters.contains(game)) {
            guildStructure.getAnnouncer().getStreamSemaphore().release();
            return ("A filter for " + game + " hasn't been set up");
        } else {
            dbHandler.deleteGameForGuild(message.getGuild(), game);
            guildStructure.getAnnouncer().getStreamSemaphore().release();
            guildStructure.removeGameFilter(game);
            return ("The filter for " + game + " will be removed");
        }
    }

    private Boolean checkIfChannelIDAlreadyThere(GuildStructure guildStructure, Long channelID) {
        List<ChannelDels> twitch_channels = guildStructure.getTwitch_channels();

        for (ChannelDels cd : twitch_channels) {
            if (cd.getChannelID().equals(channelID)) {
                return true;
            }
        }
        return false;
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
            if (cd.getName().equals(channelName)) {
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
        String url = String.format("https://api.twitch.tv/helix/games?name=%s", game);

        RestTemplate restTemplate = lembot.getTwitchClient().getRestClient().getRestTemplate();
        restTemplate.getInterceptors().add(new HeaderRequestInterceptor("Client-ID", lembot.getTwitchClient().getClientId()));

        try {
            List<models.Game> games = restTemplate.getForObject(url, Games.class).getGames();
            if (games.isEmpty()) {
                return null;
            }
            else {
                return games.get(0).getId();
            }
        }
        catch (RestException restException) {
            restException.printStackTrace();
        }

        return null;
    }

    private EmbedObject buildGameList(String gameNames, String gameIDs, String guildName, Integer partNumber) {
        EmbedBuilder builder = new EmbedBuilder();

        if (partNumber > 1) {
            builder.withAuthorName("Game filter list (pt. " + (partNumber - 1) + ") for " + guildName);
        }
        else {
            builder.withAuthorName("Game filter list for " + guildName);
        }

        builder.withAuthorUrl(githubLink);
        builder.withAuthorIcon(twitchIcon);
        builder.appendField("Game", gameNames, true);
        builder.appendField("ID", gameIDs, true);
        builder.withColor(100, 65, 164);
        builder.withThumbnail(lembot.getDiscordClient().getApplicationIconURL());


        return builder.build();
    }

    private EmbedObject buildChannelList(String channelNames, String channelIDs, String guildName, Integer partNumber) {
        EmbedBuilder builder = new EmbedBuilder();

        if (partNumber > 1) {
            builder.withAuthorName("Channel list (pt. " + (partNumber - 1) + ") for " + guildName);
        }
        else {
            builder.withAuthorName("Channel list for " + guildName);
        }

        builder.withAuthorUrl(githubLink);
        builder.withAuthorIcon(twitchIcon);
        builder.appendField("Channel", channelNames, true);
        builder.appendField("ID", channelIDs, true);
        builder.withColor(100, 65, 164);
        builder.withThumbnail(lembot.getDiscordClient().getApplicationIconURL());

        return builder.build();
    }
}

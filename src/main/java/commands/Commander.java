package commands;

import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.GameList;
import com.github.twitch4j.helix.domain.UserList;

import core.DBHandler;
import core.Lembot;

import models.ChannelDels;
import models.GuildStructure;

import org.apache.commons.lang3.StringUtils;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;

import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Commander {
    private static final String discordIcon = "https://abload.de/img/dsicordlogowzcf7.png";
    private static final String twitchIcon = "https://abload.de/img/twitchicono8dj7.png";
    private static final String githubLink = "https://github.com/lembox/lembot";

    private Lembot lembot;
    private DBHandler dbHandler;
    private Logger logger;
    private TwitchHelix helix;

    public Commander(Lembot lembot) {
        this.lembot = lembot;
        dbHandler = lembot.getDbHandler();
        logger = lembot.getLogger();
    }

    public void processCommand(Message message, String prefix) {
        boolean reactionFlag = true;

        User sender = message.getAuthor().asUser().orElse(null);
        TextChannel channel = message.getChannel();
        Server guild = message.getServer().get();
        helix = lembot.getTwitchClient().getHelix();

        GuildStructure guildStructure = lembot.provideGuildStructure(guild.getId());

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
                        TextChannel announce_channel = (TextChannel) lembot.getDiscordClient().getChannelById(Snowflake.of(channelID)).block();
                        if (!channelID.equals(announce_channel.getId().asLong())) throw new Exception("Text channel " + announce_channel.getName() + " could not be found");
                        dbHandler.setAnnounceChannel(guild.getId().asLong(), channelID);
                        lembot.sendMessage(channel, "The announcement channel has been set to: <#" + channelID + ">");

                        try {
                            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        guildStructure.setAnnounce_channel(channelID);
                        guildStructure.getAnnouncer().getStreamSemaphore().release();
                    } catch (NumberFormatException e) {
                        lembot.sendMessage(channel, "The provided channelID is not an integer.");
                    } catch (Exception e) {
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
                    lembot.sendMessage(channel, embedCreateSpec -> {
                        embedCreateSpec.setAuthor("Lembot configuration for " + guild.getName(), githubLink, discordIcon);

                        embedCreateSpec.addField("Announcement channel", "<#" + guildStructure.getAnnounce_channel() + ">", true);

                        if (guildStructure.getCleanup()) {
                            embedCreateSpec.addField("Message cleanup", "activated", true);
                        }
                        else {
                            embedCreateSpec.addField("Message cleanup", "deactivated", true);
                        }

                        if (guildStructure.getMessage_style().equals(0)) {
                            embedCreateSpec.addField("Message style", "classic", true);
                        }
                        else {
                            embedCreateSpec.addField("Message style", "embedded", true);
                        }

                        embedCreateSpec.setColor(new Color(114,137,218));
                        embedCreateSpec.setThumbnail(lembot.getDiscordClient().getApplicationInfo().block().getIcon(Image.Format.PNG).orElse(""));
                    });
                    break;
                case "maintainer_add":
                    if (sender.getId().asLong() == guild.getOwner().block().getId().asLong()) {
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
                    if (sender.getId().asLong() == guild.getOwnerId().asLong()) {
                        // in case it's of the form @userName - otherwise nothing happens
                        command[1] = command[1].replace("<@", "");
                        command[1] = command[1].replace(">", "");

                        try {
                            Long maintainerID = Long.parseLong(command[1]);
                            if (maintainerID.equals(guild.getOwnerId().asLong())) {
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
                    List<Long> maintainers = dbHandler.getMaintainers(guild.getId().asLong());
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("The maintainers of the bot in this guild are: \n");

                    for (Long l : maintainers) {
                        stringBuilder.append(lembot.getDiscordClient().getUserById(Snowflake.of(l)).block().getUsername()).append("\n");
                    }

                    lembot.sendMessage(channel, stringBuilder.toString());
                    break;
                case "game_add":
                    try {
                        if (!command[1].equals("")) {
                            if (command[1].contains("|")) {
                                String[] games = command[1].split("\\|");
                                add_games(games, guildStructure, channel, message);
                            } else {
                                add_games(new String[]{command[1]}, guildStructure, channel, message);
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

                    List<String> gameFilters = dbHandler.getGamesForGuild(guild.getId().asLong());
                    Map<String, String> gameMap = new LinkedHashMap<>();

                    if (gameFilters.isEmpty()) {
                        lembot.sendMessage(channel, "No game filters have been set up yet");
                    }
                    else {
                        for (String game : gameFilters) {
                            gameMap.put(game, null);
                        }
                        try {
                            GameList gameList = lembot.getGames(null, gameFilters);
                            List<Game> games = gameList.getGames();

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
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            String[] channels = command[1].split("\\|");
                            add_channels(channels, guildStructure, channel, message);
                        } else {
                            add_channels(new String[]{command[1]}, guildStructure, channel, message);
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

                    List<ChannelDels> twitch_channels = dbHandler.getChannelsForGuild(guild.getId().asLong());

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
                    lembot.sendMessage(channel, "Lembot is online announcing selected Twitch channels streaming selected games, last update: 25.02.2020");
                    break;
                default:
                    reactionFlag = false;
                    break;
            }
            if (reactionFlag) {
                logger.debug("Command {} is used by {} in guild {} (id: {})", command[0], sender.getUsername(), guild.getName(), guild.getId().asLong());

                try {
                    ReactionEmoji reaction = ReactionEmoji.unicode("\uD83D\uDC4C");
                    message.addReaction(reaction).subscribe();
                }
                catch (ClientException mpe) {
                    logger.error("Reaction to command {} used in guild {} (id: {}) couldn't be added", command[0], guild.getName(), guild.getId().asLong(), mpe);
                }
            }
        }
    }

    private void add_channels(String[] channelNames, GuildStructure guildStructure, Channel channel, Message message) {
        StringBuilder response = new StringBuilder();

        List<String> newChannelNames = new ArrayList<>();
        List<String> newChannelIDs = new ArrayList<>();

        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            logger.error("Interrupted access of streamSemaphore during the addition of games for guild {}", guildStructure.getGuild_id(), e);
        }

        for (String c : channelNames) {
            Long channelID = null;
            ChannelDels cd;
            System.out.println(c);
            if(StringUtils.isNumeric(c)) {
                channelID = Long.parseLong(c);
                cd = findChannelDelsWithIDAlreadyThere(guildStructure, channelID);
            }
            else {
                cd = findChannelDelsWithIDAlreadyThere(guildStructure, c);
            }

            if (cd != null) {
                response.append("Channel: ").append(cd.getName()).append(" has already been added\n");
            }
            else {
                if (channelID != null) {
                    newChannelIDs.add(c);
                }
                else {
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
                UserList userList = lembot.getUsers(newChannelIDs.isEmpty()? null : newChannelIDs, newChannelNames.isEmpty()? null : newChannelNames);
                List<com.github.twitch4j.helix.domain.User> users = userList.getUsers();

                for (com.github.twitch4j.helix.domain.User u : users) {
                    newChannelNames.remove(u.getDisplayName().toLowerCase());
                    newChannelIDs.remove(u.getId());
                    dbHandler.addChannelForGuild(message, u.getId(), u.getDisplayName());
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

        guildStructure.getAnnouncer().getStreamSemaphore().release();

        for (String n : newChannelNames) {
            response.append("Channel: ").append(n).append(" cannot be found.\n");

            if (response.length() > 1500) {
                lembot.sendMessage(channel, response.toString());
                response = new StringBuilder();
            }
        }

        for (String l : newChannelIDs) {
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
                    dbHandler.deleteChannelForGuild(message.getGuild().block(), cd.getChannelID());

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
                    dbHandler.deleteChannelForGuild(message.getGuild().block(), cd.getChannelID());
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

    private void add_games(String[] games, GuildStructure guildStructure, Channel channel, Message message) {
        StringBuilder response = new StringBuilder();

        List<String> newGames = new ArrayList<>();

        try {
            guildStructure.getAnnouncer().getStreamSemaphore().acquire();
        } catch (InterruptedException e) {
            logger.error("Interrupted access of streamSemaphore during the addition of games for guild {}",  guildStructure.getGuild_id(), e);
        }

        Map<String, String> gameFilterz = guildStructure.getGame_filters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        for (String g : games) {
            String key = gameFilterz.get(g);

            if (key != null) {
                response.append("A filter for ").append(g).append(" has already been set up\n");
            }
            else {
                newGames.add(g);
            }

            if (response.length() > 1500) {
                lembot.sendMessage(channel, response.toString());
                response = new StringBuilder();
            }
        }

        try {
            GameList gameList = lembot.getGames(null, newGames);
            List<Game> gamez = gameList.getGames();

            for (Game g : gamez) {
                newGames.remove(g.getName().toLowerCase());
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
        }
        else {
            String some_game = game.toLowerCase();
            String key = guildStructure.getGame_filters().entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().equals(some_game))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

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
            GameList gameList = lembot.getGames(null, games);
            List<Game> gamez = gameList.getGames();

            if (gamez.isEmpty()) {
                return null;
            }
            else {
                return gameList.getGames().get(0).getId();
            }
        } catch (Exception e) {
            logger.error("Getting Game IDs failed", e);
            return null;
        }

    }

    private Consumer<EmbedCreateSpec> buildGameList(String gameNames, String gameIDs, String guildName, Integer partNumber) {
        return spec -> {
            if (partNumber > 1) {
                spec.setAuthor("Game filter list (pt. " + (partNumber - 1) + ") for " + guildName, githubLink, twitchIcon);
            }
            else {
                spec.setAuthor("Game filter list for " + guildName, githubLink, twitchIcon);
            }

            spec.addField("Game", gameNames, true);
            spec.addField("ID", gameIDs, true);
            spec.setColor(new Color( 100, 65, 164));

            spec.setThumbnail(lembot.getDiscordClient().getApplicationInfo().block().getIcon(Image.Format.PNG).orElse(""));
        };
    }

    private Consumer<EmbedCreateSpec> buildChannelList(String channelNames, String channelIDs, String guildName, Integer partNumber) {
        return spec -> {
            if (partNumber > 1) {
                spec.setAuthor("Channel list (pt. " + (partNumber - 1) + ") for " + guildName, githubLink, twitchIcon);
            }
            else {
                spec.setAuthor("Channel list for " + guildName, githubLink, twitchIcon);
            }

            spec.addField("Channel", channelNames, true);
            spec.addField("ID", channelIDs, true);
            spec.setColor(new Color(100, 65, 164));

            spec.setThumbnail(lembot.getDiscordClient().getApplicationInfo().block().getIcon(Image.Format.PNG).orElse(""));
        };
    }
}

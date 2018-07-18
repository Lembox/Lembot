package commands;

import core.DBHandler;
import core.Lembot;

import me.philippheuer.twitch4j.TwitchClient;
import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import models.ChannelDels;
import org.apache.commons.lang3.StringUtils;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import java.util.List;

public class Commander {
    public static void processCommand(IMessage message, String prefix) {
        IUser sender = message.getAuthor();
        IChannel channel = message.getChannel();
        IGuild guild = message.getGuild();

        String[] command = message.getContent().replaceFirst(prefix, "").split(" ", 2);

        command[0] = command[0].toLowerCase();

        switch(command[0]) {
            case "init":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    Lembot.sendMessage(channel,"In which channel should I announce the streams? Please provide the channel ID, to do so activate the developer mode: User Settings -> Appearance -> Developer Mode. Then right-click on the channel -> Copy ID and paste the resulting ID as follows: '!set_announce ID'. Afterwards you're set up to add Twitch channels and game filters, refer to !help or !commands for help");
                }
                break;
            case "set_announce":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    try  {
                        Long channelID = Long.parseLong(command[1]);
                        Lembot.getDbHandler().setAnnounceChannel(guild.getLongID(), channelID);
                        IChannel announce_channel = Lembot.getDiscordClient().getChannelByID(channelID);
                        Lembot.sendMessage(channel,"The announcement channel has been set to: " + announce_channel.toString());

                        Lembot.provideGuildStructure(guild.getLongID()).setAnnounce_channel(channelID);
                    }
                    catch (NumberFormatException e) {
                        Lembot.sendMessage(channel,"The provided channelID is not an integer");
                    }
                    catch (DiscordException de) {
                        Lembot.sendMessage(channel,"The provided channelID is not valid");
                    }
                }
                break;
            case "maintainer_add":
                if (sender.getLongID() == guild.getOwnerLongID()) {
                    try {
                        Lembot.getDbHandler().addMaintainerForGuild(message, Long.parseLong(command[1]));
                        Lembot.sendMessage(channel,"<@" + command[1] + "> has been added as maintainer");
                    }
                    catch (NumberFormatException ne) {
                        Lembot.sendMessage(channel,"The user_id is not valid");
                    }
                }
                break;
            case "maintainer_remove":
                if (sender.getLongID() == guild.getOwnerLongID()) {
                    try {
                        Lembot.getDbHandler().deleteMaintainerForGuild(message, Long.parseLong(command[1]));
                        Lembot.sendMessage(channel,"<@" + command[1] + "> has been removed as maintainer");
                    }
                    catch (NumberFormatException ne) {
                        Lembot.sendMessage(channel,"The user_id is not valid");
                    }
                }
                break;
            case "maintainers":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    List<Long> maintainers = Lembot.getDbHandler().getMaintainers(guild.getLongID());
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("The maintainers of the bot in this guild are: \n");

                    for (Long l : maintainers) {
                        stringBuilder.append(Lembot.getDiscordClient().getUserByID(l).getName()).append(" \n");
                    }

                    Lembot.sendMessage(channel, stringBuilder.toString());
                }
                break;
            case "game_add":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    List<String> gameFilters = Lembot.provideGuildStructure(guild.getLongID()).getGame_filters();

                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            String[] games = command[1].split("\\|");

                            for (String game : games) {
                                if (gameFilters.contains(game)) {
                                    Lembot.sendMessage(channel,"A filter for " + game + " has already been set up");
                                }
                                else {
                                    Lembot.getDbHandler().addGameForGuild(message, game);
                                    Lembot.sendMessage(channel,"A filter for " + game + " will be added");
                                    Lembot.provideGuildStructure(guild.getLongID()).addGameToBeAdded(game);
                                }
                            }
                        }
                        else {
                            if (gameFilters.contains(command[1])) {
                                Lembot.sendMessage(channel,"A filter for " + command[1] + " has already been set up");
                            }
                            else {
                                Lembot.getDbHandler().addGameForGuild(message, command[1]);
                                Lembot.sendMessage(channel,"A filter for " + command[1] + " will be added");
                                Lembot.provideGuildStructure(guild.getLongID()).addGameToBeAdded(command[1]);
                            }
                        }
                    }
                    else {
                        message.getChannel().sendMessage("Use the command properly");
                    }
                }
                break;
            case "game_remove":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    List<String> gameFilters = Lembot.provideGuildStructure(guild.getLongID()).getGame_filters();

                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            String[] games = command[1].split("\\|");

                            for (String game : games) {
                                if (!gameFilters.contains(game)) {
                                    Lembot.sendMessage(channel,"A filter for " + game + " hasn't been set up");
                                }
                                else {
                                    Lembot.getDbHandler().deleteGameForGuild(message, game);
                                    Lembot.sendMessage(channel,"The filter for " + game + " will be removed");
                                    Lembot.provideGuildStructure(guild.getLongID()).addGameToBeRemoved(game);
                                }
                            }
                        }
                        else {
                            if (!gameFilters.contains(command[1])) {
                                Lembot.sendMessage(channel,"A filter for " + command[1] + " hasn't been set up");
                            }
                            else {
                                Lembot.getDbHandler().deleteGameForGuild(message, command[1]);
                                Lembot.sendMessage(channel,"The filter for " + command[1] + " will be removed");
                                Lembot.provideGuildStructure(guild.getLongID()).addGameToBeRemoved(command[1]);
                            }

                        }
                    }
                    else {
                        message.getChannel().sendMessage("Use the command properly");
                    }
                }
                break;
            case "game_filters":
                StringBuilder stringBuilder = new StringBuilder();
                List<String> gameFilters = Lembot.provideGuildStructure(guild.getLongID()).getGame_filters();
                List<String> gamesToBeAdded = Lembot.provideGuildStructure(guild.getLongID()).getGamesToBeAdded();
                List<String> gamesToBeRemoved = Lembot.provideGuildStructure(guild.getLongID()).getGamesToBeRemoved();

                if (Lembot.getDbHandler().isMaintainer(message)) {
                    for (String game : gameFilters) {
                        if (gamesToBeRemoved.contains(game)) {
                            stringBuilder.append(game).append(" will be removed \n");
                        }
                        else {
                            stringBuilder.append(game).append("\n");
                        }
                    }
                    for (String game : gamesToBeAdded) {
                        stringBuilder.append(game).append(" will be added \n");
                    }

                    String response = stringBuilder.toString();

                    if (response.equals("")) {
                        Lembot.sendMessage(channel, "No game filters have been set up yet");
                    }
                    else {
                        Lembot.sendMessage(channel, stringBuilder.toString());
                    }
                }
                break;
            case "twitch_add":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            String[] channels = command[1].split("\\|");

                            for (String s : channels) {
                                add_channel(s, message);
                            }
                        }
                        else {
                            add_channel(command[1], message);
                        }
                    }
                    else {
                        Lembot.sendMessage(channel,"Use the command properly");
                    }
                }
                break;
            case "twitch_remove":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    if (!command[1].equals("")) {
                        if (command[1].contains("|")) {
                            String[] channels = command[1].split("\\|");

                            for (String s : channels) {
                                System.out.println(s);
                                remove_channel(s, message);
                            }
                        }
                        else {
                            remove_channel(command[1], message);
                        }
                    }
                    else {
                        Lembot.sendMessage(channel,"Use the command properly");
                    }
                }
                break;
            case "twitch_channels":
                StringBuilder stringBuilder1 = new StringBuilder();

                if (Lembot.getDbHandler().isMaintainer(message)) {
                    List<ChannelDels> twitch_channels = Lembot.provideGuildStructure(guild.getLongID()).getTwitch_channels();
                    List<ChannelDels> channelsToBeRemoved = Lembot.provideGuildStructure(guild.getLongID()).getChannelsToBeRemoved();
                    List<ChannelEndpoint> channelsToBeAdded = Lembot.provideGuildStructure(guild.getLongID()).getChannelsToBeAdded();

                    for (ChannelDels cd : twitch_channels) {
                        if (channelsToBeRemoved.contains(cd)) {
                            stringBuilder1.append(cd.getChannelEndpoint().getChannel().getName()).append(" with ID: ").append(cd.getChannelID()).append("will be removed \n");
                        }
                        else {
                            stringBuilder1.append(cd.getChannelEndpoint().getChannel().getName()).append(" with ID: ").append(cd.getChannelID()).append("\n");
                        }
                    }
                    for (ChannelEndpoint ce : channelsToBeAdded) {
                        stringBuilder1.append(ce.getChannel().getName()).append(" with ID: ").append(ce.getChannelId()).append(" will be added \n");
                    }
                }

                String response_c = stringBuilder1.toString();

                if (response_c.equals("")) {
                    Lembot.sendMessage(channel,"No channels have been added yet.");
                }
                else {
                    Lembot.sendMessage(channel, response_c);
                }
                break;
            case "cleanup":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    Boolean result = Lembot.getDbHandler().updateCleanup(guild.getLongID());

                    if (result) {
                        Lembot.sendMessage(channel,"Message cleanup has been activated");
                    } else {
                        Lembot.sendMessage(channel,"Message cleanup has been deactivated");
                    }
                }
                break;
            case "channel_del":     // check values in ChannelDel
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    ChannelDels cd = findChannelDelsWithIDAlreadyThere(guild, command[1]);
                    Lembot.sendMessage(channel,cd.getName() + " " + cd.getLive() + " " + cd.getOffline_flag());
                }
                break;
            case "help":
            case "commands":
                if (Lembot.getDbHandler().isMaintainer(message)) {
                    channel.sendMessage("https://github.com/Lembox/Lembot/tree/master");
                }
                break;
        }
    }

    private static void add_channel(String channel, IMessage message) {
        IChannel text_channel = message.getChannel();
        IGuild guild = message.getGuild();

        if(StringUtils.isNumeric(channel)) {
            // assuming input is ChannelID
            try {
                ChannelEndpoint ce = Lembot.getTwitchClient().getChannelEndpoint(Long.parseLong(channel));
                if (!checkIfChannelEndpointWithIDAlreadyThere(guild, Long.parseLong(channel))) {
                    Lembot.getDbHandler().addChannelForGuild(message, ce.getChannelId(), ce.getChannel().getName());
                    Lembot.provideGuildStructure(guild.getLongID()).addChannelToBeAdded(ce);
                    text_channel.sendMessage("Channel " + ce.getChannel().getName() + " with ID: " + ce.getChannelId() + " will be added");
                }
                else {
                    text_channel.sendMessage("Channel " + ce.getChannel().getName() +  " has been already added");
                }
            }
            catch (Exception e) {
                text_channel.sendMessage("Something went wrong");
            }
        }
        else {
            // assuming input is ChannelName
            try {
                ChannelEndpoint ce = Lembot.getTwitchClient().getChannelEndpoint(channel.toLowerCase());
                if (!checkIfChannelEndpointWithIDAlreadyThere(guild, ce.getChannelId())) {
                    Lembot.getDbHandler().addChannelForGuild(message, ce.getChannelId(), ce.getChannel().getName());
                    Lembot.provideGuildStructure(guild.getLongID()).addChannelToBeAdded(ce);
                    text_channel.sendMessage("Channel " + ce.getChannel().getName() + " with ID: " + ce.getChannelId() + " will be added");
                }
                else {
                    text_channel.sendMessage("Channel " + ce.getChannel().getName() +  " has already been added");
                }
            }
            catch (Exception e) {
                text_channel.sendMessage("Something went wrong");
            }
        }
    }

    private static void remove_channel(String channel, IMessage message) {
        IChannel text_channel = message.getChannel();
        IGuild guild = message.getGuild();

        if(StringUtils.isNumeric(channel)) {
            // assuming input is ChannelID
            try {
                ChannelDels cd = findChannelDelsWithIDAlreadyThere(guild, Long.parseLong(channel));
                if (cd != null) {
                    if (cd.getPostID() != null) {
                        Lembot.deleteMessage(Lembot.getDiscordClient().getChannelByID(Lembot.provideGuildStructure(guild.getLongID()).getAnnounce_channel()), cd.getPostID());
                    }
                    Lembot.provideGuildStructure(guild.getLongID()).addChannelToBeRemoved(cd);
                    Lembot.getDbHandler().deleteChannelForGuild(message, cd.getChannelID());
                    Lembot.sendMessage(text_channel, "Channel " + cd.getChannelEndpoint().getChannel().getName() + "with ID: " + cd.getChannelID() + " will be removed");
                }
                else {
                    Lembot.sendMessage(text_channel,"Channel with the ID: " + channel + " has already been removed or has never been added");
                }
            }
            catch (Exception e) {
                Lembot.sendMessage(text_channel,"Something went wrong");
            }
        }
        else {
            // assuming input is ChannelName
            try {
                ChannelDels cd = findChannelDelsWithIDAlreadyThere(guild, channel.toLowerCase());
                if (cd != null) {
                    if (cd.getPostID() != null) {
                        Lembot.deleteMessage(Lembot.getDiscordClient().getChannelByID(Lembot.provideGuildStructure(guild.getLongID()).getAnnounce_channel()), cd.getPostID());
                    }
                    Lembot.provideGuildStructure(guild.getLongID()).addChannelToBeRemoved(cd);
                    Lembot.getDbHandler().deleteChannelForGuild(message, cd.getChannelID());
                    Lembot.sendMessage(text_channel,"Channel " + cd.getChannelEndpoint().getChannel().getName() + " with ID: " + cd.getChannelID() + " will be removed");
                }
                else {
                    Lembot.sendMessage(text_channel,"Channel with name: " + channel + " has already been removed");
                }
            }
            catch (Exception e) {
                Lembot.sendMessage(text_channel,"Something went wrong");
            }
        }
    }

    private static Boolean checkIfChannelEndpointWithIDAlreadyThere(IGuild guild, Long channelID) {
        List<ChannelDels> twitch_channels = Lembot.provideGuildStructure(guild.getLongID()).getTwitch_channels();

        for (ChannelDels cd : twitch_channels) {
            if (cd.getChannelID().equals(channelID)) {
                return true;
            }
        }
        return false;
    }

    private static ChannelDels findChannelDelsWithIDAlreadyThere(IGuild guild, Long channelID) {
        List<ChannelDels> twitch_channels = Lembot.provideGuildStructure(guild.getLongID()).getTwitch_channels();

        for (ChannelDels cd : twitch_channels) {
            if (cd.getChannelID().equals(channelID)) {
                return cd;
            }
        }
        return null;
    }

    private static ChannelDels findChannelDelsWithIDAlreadyThere(IGuild guild, String channelName) {
        List<ChannelDels> twitch_channels = Lembot.provideGuildStructure(guild.getLongID()).getTwitch_channels();

        for (ChannelDels cd : twitch_channels) {
            if (cd.getChannelEndpoint().getChannel().getName().equals(channelName)) {
                return cd;
            }
        }
        return null;
    }
}

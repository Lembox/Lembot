package core;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import listeners.DiscordHandler;
import listeners.GuildHandler;
import listeners.MessageHandler;
import me.philippheuer.twitch4j.TwitchClientBuilder;
import me.philippheuer.twitch4j.TwitchClient;

import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import models.ChannelDels;
import models.GuildStructure;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.RequestBuffer;

public class Lembot {
    private static TwitchClient twitchClient;
    private static IDiscordClient discordClient;

    private static DBHandler dbHandler;
    private static List<GuildStructure> allChannels = new ArrayList<>();
    private static StreamAnnouncer announcer;

    public Lembot() {
        Properties properties = new Properties();
        String db_path;

        try {
            properties.load(new FileReader("bot_config.txt"));   // get config from file
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            db_path = properties.get("db_path").toString();
        }
        catch (NullPointerException e) {
            db_path = null;
        }

        if (db_path != null) {
            dbHandler = new DBHandler(db_path);
        }
        else {
            dbHandler = new DBHandler();
        }

        try {
            discordClient = new ClientBuilder()
                    .withToken(properties.get("discord_token").toString())
                    .login();

            EventDispatcher dispatcher = discordClient.getDispatcher();
            dispatcher.registerListener(new GuildHandler());
            dispatcher.registerListener(new MessageHandler());
            dispatcher.registerListener(new DiscordHandler());
        }
        catch (DiscordException de) {
            de.printStackTrace();
        }

        twitchClient = TwitchClientBuilder.init()
                .withClientId(properties.get("twitch_clientID").toString())
                .withClientSecret(properties.get("twitch_clientSecret").toString())
                .withAutoSaveConfiguration(true)
                .withConfigurationDirectory(new File("config"))
                .withCredential(properties.get("twitch_oauth").toString()) // Get your token at: https://twitchapps.com/tmi/
                .connect();

        init();

        announcer = new StreamAnnouncer(allChannels);
    }

    private void init() {
        // Read necessary information from DB
        List<Long[]> guilds = dbHandler.getGuilds();
        List<IGuild> connected_guilds = discordClient.getGuilds();
        Long guildID;
        Long announce_channel;
        List<ChannelDels> twitch_channels = new ArrayList<>();
        List<String> gameFilters = new ArrayList<>();

        List<Long> connected_guildIDs = new ArrayList<>();

        for (IGuild iGuild : connected_guilds) {
            connected_guildIDs.add(iGuild.getLongID());
        }

        for (Long[] g : guilds) {
            guildID = g[0];
            announce_channel = g[1];

            if (!connected_guildIDs.contains(guildID)) {
                Lembot.getDbHandler().removeGuild(guildID);
                Lembot.removeGuildStructure(guildID);
            }
            else {
                twitch_channels.addAll(dbHandler.getChannelsForGuild(guildID));

                for (ChannelDels cd : twitch_channels) {
                    ChannelEndpoint channelEndpoint = twitchClient.getChannelEndpoint(cd.getChannelID());
                    cd.setChannelEndpoint(channelEndpoint);
                }

                gameFilters.addAll(dbHandler.getGamesForGuild(guildID));

                GuildStructure guildStructure = new GuildStructure(guildID, twitch_channels, gameFilters, announce_channel);
                allChannels.add(guildStructure);
            }
        }
    }

    public static TwitchClient getTwitchClient() {
        return twitchClient;
    }

    public static IDiscordClient getDiscordClient() {
        return discordClient;
    }

    public static DBHandler getDbHandler() {
        return dbHandler;
    }

    public static void addGuildStructure(GuildStructure guildStructure) {
        allChannels.add(guildStructure);
    }

    public static GuildStructure provideGuildStructure(Long guildID) {
        for (GuildStructure g : allChannels) {
            if (g.getGuild_id().equals(guildID)) {
                return g;
            }
        }
        return null;
    }

    public static void sendMessage(IChannel channel, String message) {
        RequestBuffer.request(() -> {
                try{
                    channel.sendMessage(message);
                } catch (RateLimitException e){
                    System.out.println("Message: " + message + " to channel " + channel.getLongID() + " from guild " + channel.getGuild().getLongID() + " could not be sent");
                    throw e; // This makes sure that RequestBuffer will do the retry for you
                }
            });
    }

    public static Long sendMessageID(IChannel channel, String message) {
        RequestBuffer.RequestFuture<Long> id = RequestBuffer.request(() -> {
            try{
                return channel.sendMessage(message).getLongID();
            } catch (RateLimitException e){
                System.out.println("Message: " + message + " to channel " + channel.getLongID() + " from guild " + channel.getGuild().getLongID() + " could not be sent");
                throw e; // This makes sure that RequestBuffer will do the retry for you
            }
        });
        return id.get();
    }

    public static void editMessage(IChannel channel, Long messageID, String message) {
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
                        ne.printStackTrace();
                    }
                }
            }
            catch (RateLimitException re) {
                System.out.println("Message: " + messageID + " could not be edited");
                throw re;
            }
        });
    }

    public static void deleteMessage(IChannel channel, Long messageID) {
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
                       ne.printStackTrace();
                   }
               }
           }
           catch (RateLimitException re) {
               System.out.println("Message: " + messageID + " could not be deleted");
               throw re;
            }
        });
    }

    public static void setAllChannels(List<GuildStructure> allChannels) {
        Lembot.allChannels = allChannels;
    }

    public static void removeGuildStructure(Long guildID) {
        GuildStructure guildStructure = null;
        for (GuildStructure g : allChannels) {
            if (g.getGuild_id().equals(guildID)) {
                guildStructure = g;
                break;
            }
        }
        allChannels.remove(guildStructure);
    }

}

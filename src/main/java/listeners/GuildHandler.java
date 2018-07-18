package listeners;

import core.DBHandler;
import core.Lembot;
import me.philippheuer.twitch4j.TwitchClient;
import me.philippheuer.twitch4j.endpoints.ChannelEndpoint;
import models.ChannelDels;
import models.GuildStructure;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.GuildCreateEvent;
import sx.blah.discord.handle.impl.events.guild.GuildLeaveEvent;
import sx.blah.discord.handle.impl.events.guild.GuildUnavailableEvent;
import sx.blah.discord.handle.impl.events.guild.GuildUpdateEvent;
import sx.blah.discord.handle.impl.events.guild.GuildTransferOwnershipEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IPrivateChannel;
import sx.blah.discord.util.DiscordException;

import java.util.ArrayList;
import java.util.List;

public class GuildHandler {
    private DBHandler dbHandler = Lembot.getDbHandler();
    private TwitchClient twitchClient = Lembot.getTwitchClient();

    @EventSubscriber
    public void onGuildJoined(GuildCreateEvent event) {
        IGuild guild = event.getGuild();

        System.out.println(guild.getName());

        Long guild_id = dbHandler.getGuild(guild);

        if (guild_id == null) {     // if not in DB yet then the query yields the value null
            dbHandler.addTableForGuild(guild);
            dbHandler.addGuild(guild);
            dbHandler.addOwnerAsMaintainer(guild);

            Long guildID = guild.getLongID();
            Long announce_channel = guild.getDefaultChannel().getLongID();
            List<ChannelDels> twitch_channels = new ArrayList<>(dbHandler.getChannelsForGuild(guildID));
            List<String> gameFilters = new ArrayList<>(dbHandler.getGamesForGuild(guildID));

            for (ChannelDels cd : twitch_channels) {
                ChannelEndpoint channelEndpoint = twitchClient.getChannelEndpoint(cd.getChannelID());
                cd.setChannelEndpoint(channelEndpoint);
            }

            GuildStructure guildStructure = new GuildStructure(guildID, twitch_channels, gameFilters, announce_channel);
            Lembot.addGuildStructure(guildStructure);

            try {
                IPrivateChannel privateChannelToOwner = Lembot.getDiscordClient().getOrCreatePMChannel(Lembot.getDiscordClient().fetchUser(guild.getOwnerLongID()));
                privateChannelToOwner.sendMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init in the server/guild for more information");
            }
            catch (DiscordException de) {
                guild.getDefaultChannel().sendMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init for more information");
            }
        }
    }

    @EventSubscriber
    public void onGuildLeft(GuildLeaveEvent event) {
        dbHandler.removeGuild(event.getGuild().getLongID());
        Lembot.removeGuildStructure(event.getGuild().getLongID());
    }

    @EventSubscriber
    public void onTransferredOwnership(GuildTransferOwnershipEvent event) {
        dbHandler.addOwnerAsMaintainer(event.getGuild());
    }

    @EventSubscriber
    public void onGuildUnavailable(GuildUnavailableEvent event) {
        System.out.println(event.getGuild().getName() + " is not available");
    }
}

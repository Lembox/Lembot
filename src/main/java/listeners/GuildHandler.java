package listeners;

import core.Lembot;
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

import java.util.ArrayList;
import java.util.List;

public class GuildHandler {
    @EventSubscriber
    public void onGuildJoined(GuildCreateEvent event) {
        IGuild guild = event.getGuild();

        System.out.println(guild.getName());

        Long guild_id = Lembot.getDbHandler().getGuild(guild);

        if (guild_id == null) {     // if not in DB yet then the query yields the value null
            Lembot.getDbHandler().addTableForGuild(guild);
            Lembot.getDbHandler().addGuild(guild);
            Lembot.getDbHandler().addOwnerAsMaintainer(guild);

            Long guildID = guild.getLongID();
            Long announce_channel = guild.getDefaultChannel().getLongID();
            List<ChannelDels> twitch_channels = new ArrayList<>();
            List<String> gameFilters = new ArrayList<>(Lembot.getDbHandler().getGamesForGuild(guildID));

            twitch_channels.addAll(Lembot.getDbHandler().getChannelsForGuild(guildID));

            for (ChannelDels cd : twitch_channels) {
                ChannelEndpoint channelEndpoint = Lembot.getTwitchClient().getChannelEndpoint(cd.getChannelID());
                cd.setChannelEndpoint(channelEndpoint);
            }

            GuildStructure guildStructure = new GuildStructure(guildID, twitch_channels, gameFilters, announce_channel);
            Lembot.addGuildStructure(guildStructure);

            guild.getDefaultChannel().sendMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init for more information");
        }
    }

    @EventSubscriber
    public void onGuildLeft(GuildLeaveEvent event) {
        Lembot.getDbHandler().removeGuild(event.getGuild());
        Lembot.removeGuildStructure(event.getGuild().getLongID());
    }

    @EventSubscriber
    public void onTransferredOwnership(GuildTransferOwnershipEvent event) {
        Lembot.getDbHandler().addOwnerAsMaintainer(event.getGuild());
    }

    @EventSubscriber
    public void onGuildUnavailable(GuildUnavailableEvent event) {
        System.out.println(event.getGuild().getName() + " is not available");
    }
}

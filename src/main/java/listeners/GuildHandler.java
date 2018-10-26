package listeners;

import core.DBHandler;
import core.Lembot;
import core.StreamAnnouncer;
import models.GuildStructure;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.GuildCreateEvent;
import sx.blah.discord.handle.impl.events.guild.GuildLeaveEvent;
import sx.blah.discord.handle.impl.events.guild.GuildUnavailableEvent;
import sx.blah.discord.handle.impl.events.guild.GuildTransferOwnershipEvent;
import sx.blah.discord.handle.impl.events.guild.channel.ChannelDeleteEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IPrivateChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;

import java.util.ArrayList;

public class GuildHandler {
    private DBHandler dbHandler;
    private Lembot lembot;

    public GuildHandler(Lembot lembot) {
        this.lembot = lembot;
        dbHandler = lembot.getDbHandler();
    }

    @EventSubscriber
    public void onGuildJoined(GuildCreateEvent event) {
        IGuild guild = event.getGuild();

        Long guild_id = dbHandler.getGuild(guild);

        if (guild_id == null) {     // if not in DB yet then the query yields the value null
            lembot.getLogger().info("New guild {} with name {} joined", guild.getLongID(), guild.getName());
            dbHandler.addTableForGuild(guild);
            dbHandler.addGuild(guild);
            dbHandler.addOwnerAsMaintainer(guild);

            Long guildID = guild.getLongID();
            Long announce_channel = guild.getDefaultChannel().getLongID();

            GuildStructure guildStructure = new GuildStructure(guildID, new ArrayList<>(), new ArrayList<>(), announce_channel, lembot);
            guildStructure.setAnnouncer(new StreamAnnouncer(guildStructure));
            lembot.addGuildStructure(guildStructure);

            try {
                lembot.sendMessage(guild.getDefaultChannel(), "Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init for more information");
            }
            catch (DiscordException | MissingPermissionsException e) {
                lembot.getLogger().error("Error occured while joining guild {}", guildID, e);
                IPrivateChannel privateChannelToOwner = lembot.getDiscordClient().getOrCreatePMChannel(lembot.getDiscordClient().fetchUser(guild.getOwnerLongID()));
                privateChannelToOwner.sendMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init in the server/guild for more information");
            }
        }
        else {
            lembot.getLogger().info("Rejoined guild {} with name {}", guild.getLongID(), guild.getName());
        }
    }

    @EventSubscriber
    public void onGuildLeft(GuildLeaveEvent event) {
        IGuild guild = event.getGuild();
        lembot.removeGuildStructure(guild.getLongID());
        dbHandler.removeGuild(guild.getLongID());
        lembot.getLogger().warn("Got kicked from guild {} with name {} and removed the structures", guild.getLongID(), guild.getName());
    }

    @EventSubscriber
    public void onTransferredOwnership(GuildTransferOwnershipEvent event) {
        IGuild guild = event.getGuild();

        dbHandler.addOwnerAsMaintainer(guild);
        lembot.getLogger().info("Guild {} with name {} has a new owner who was added as maintainer", guild.getLongID(), guild.getName());
    }

    @EventSubscriber
    public void onGuildUnavailable(GuildUnavailableEvent event) {
        IGuild guild = event.getGuild();

        try {
            lembot.getLogger().error("Guild {} with name {} is unavailable", guild.getLongID(), guild.getName());
        } catch (NullPointerException npe) {
            lembot.getLogger().error("A guild is unavailable", npe);
        }
    }

    @EventSubscriber
    public void onChannelDeleted(ChannelDeleteEvent event) {
        IGuild guild = event.getGuild();
        GuildStructure guildStructure = lembot.provideGuildStructure(guild.getLongID());

        if (guildStructure.getAnnounce_channel().equals(event.getChannel().getLongID())) {
            guildStructure.setAnnounce_channel(guild.getDefaultChannel().getLongID());
            dbHandler.setAnnounceChannel(guild.getLongID(), guild.getDefaultChannel().getLongID());
            lembot.sendMessage(guild.getDefaultChannel(), "My announcement channel #" + event.getChannel().getName() + " was deleted, so it was changed to <#" + guild.getDefaultChannel().getLongID() + ">.");
            lembot.getLogger().info("Guild {} (id: {}) deleted the announcement channel so the default channel was set up", guild.getName(), guild.getLongID());
        }
    }
}

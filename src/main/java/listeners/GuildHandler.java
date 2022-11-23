package listeners;

import core.DBHandler;
import core.Lembot;
import core.StreamAnnouncer;
import models.GuildStructure;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.channel.server.ServerChannelDeleteEvent;
import org.javacord.api.event.server.*;
import org.javacord.api.listener.channel.server.ServerChannelDeleteListener;
import org.javacord.api.listener.server.*;

import java.util.ArrayList;
import java.util.HashMap;

public class GuildHandler implements ServerJoinListener, ServerLeaveListener, ServerBecomesAvailableListener, ServerBecomesUnavailableListener, ServerChangeOwnerListener, ServerChannelDeleteListener {
    private DBHandler dbHandler;
    private Lembot lembot;

    public GuildHandler(Lembot lembot) {
        this.lembot = lembot;
        dbHandler = lembot.getDbHandler();
    }

    @Override
    public void onServerJoin(ServerJoinEvent event) {
        Server guild = event.getServer();
        Long guild_id = dbHandler.getGuild(guild);
        Long guildID = guild.getId();

        if (guild_id == null) {
            lembot.getLogger().info("New guild {} with name {} joined", guild.getId(), guild.getName());
            dbHandler.addTableForGuild(guild);
            dbHandler.addGuild(guild);
            dbHandler.addOwnerAsMaintainer(guild);

            Long announce_channel = null;

            GuildStructure guildStructure = new GuildStructure(guildID, new ArrayList<>(), new HashMap<>(), announce_channel, lembot);
            guildStructure.setAnnouncer(new StreamAnnouncer(guildStructure));
            lembot.addGuildStructure(guildStructure);

            try {
                PrivateChannel privateChannelToOwner = lembot.getDiscordApi().getUserById(guild.getOwnerId()).get().getPrivateChannel().get();
                privateChannelToOwner.sendMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init in a channel of your server/guild with rights for me to read and write in for more information");
            }
            catch (Exception e) {
                lembot.getLogger().error("Error occured while joining guild {}", guildID, e);
            }
        }
        else {
            lembot.getLogger().info("Rejoined guild {} with name {}", guildID, guild.getName());
        }
    }
    @Override
    public void onServerLeave(ServerLeaveEvent event) {
        Server guild = event.getServer();
        Long guildID = guild.getId();

        lembot.removeGuildStructure(guildID);
        dbHandler.removeGuild(guildID);
        lembot.getLogger().warn("Got kicked from guild {} with name {} and removed the structures", guildID, guild.getName());
    }
    @Override
    public void onServerBecomesAvailable(ServerBecomesAvailableEvent event) {
        Server guild = event.getServer();
        Long guildID = guild.getId();
    }
    @Override
    public void onServerBecomesUnavailable(ServerBecomesUnavailableEvent event) {
        Server guild = event.getServer();
        Long guildID = guild.getId();

        lembot.getLogger().warn("Outage, can't connect to guild {} with name {}", guild, guild.getName());
    }

    @Override
    public void onServerChangeOwner(ServerChangeOwnerEvent event) {
        Server guild = event.getServer();

        dbHandler.addOwnerAsMaintainer(guild);
        lembot.getLogger().info("Guild {} with name {} has a new owner who was added as maintainer", guild.getId(), guild.getName());
    }

    @Override
    public void onServerChannelDelete(ServerChannelDeleteEvent event) {
        Server guild = event.getServer();
        Long guildID = guild.getId();
        Long channelID = event.getChannel().getId();
        ServerTextChannel defaultChannel = guild.getChannels().get(0).asServerTextChannel().get();

        GuildStructure guildStructure = lembot.provideGuildStructure(guildID);

        try {
            if (guildStructure.getAnnounce_channel().equals(channelID)) {
                guildStructure.setAnnounce_channel(defaultChannel.getId());
                dbHandler.setAnnounceChannel(guildID, defaultChannel.getId());
                lembot.sendMessage(defaultChannel, "My announcement channel #" + event.getChannel().getName() + " was deleted, so it was changed to <#" + defaultChannel.getId() + ">.");
                lembot.getLogger().info("Guild {} (id: {}) deleted the announcement channel so the default channel was set up", guild.getName(), guildID);
            }
        } catch (Exception e) {
            lembot.getLogger().warn("blubb", e);
        }
    }
}

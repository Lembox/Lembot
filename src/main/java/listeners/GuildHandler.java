package listeners;

import core.DBHandler;
import core.Lembot;
import core.StreamAnnouncer;
import discord4j.core.object.entity.*;
import discord4j.rest.http.client.ClientException;
import models.GuildStructure;

import java.util.ArrayList;
import java.util.HashMap;

public class GuildHandler {
    private DBHandler dbHandler;
    private Lembot lembot;

    public GuildHandler(Lembot lembot) {
        this.lembot = lembot;
        dbHandler = lembot.getDbHandler();
    }

    public void onGuildJoined(Guild guild) {
        Long guild_id = dbHandler.getGuild(guild);
        Long guildID = guild.getId().asLong();

        if (guild_id == null) {     // if not in DB yet then the query yields the value null
            lembot.getLogger().info("New guild {} with name {} joined", guild.getId().asLong(), guild.getName());
            dbHandler.addTableForGuild(guild);
            dbHandler.addGuild(guild);
            dbHandler.addOwnerAsMaintainer(guild);

            Long announce_channel = null;

            GuildStructure guildStructure = new GuildStructure(guildID, new ArrayList<>(), new HashMap<>(), announce_channel, lembot);
            guildStructure.setAnnouncer(new StreamAnnouncer(guildStructure));
            lembot.addGuildStructure(guildStructure);

            try {
                PrivateChannel privateChannelToOwner = guild.getOwner().block().getPrivateChannel().block();
                assert privateChannelToOwner != null;
                privateChannelToOwner.createMessage("Thanks for inviting me to " + guild.getName() + ". Be sure to set me up properly, use the command !init in a channel of your server/guild with rights for me to read and write in for more information").subscribe();
            }
            catch (ClientException e) {
                lembot.getLogger().error("Error occured while joining guild {}", guildID, e);
            }
        }
        else {
            lembot.getLogger().info("Rejoined guild {} with name {}", guildID, guild.getName());
        }
    }

    public void onGuildLeft(Guild guild, Boolean unavailable) {
        Long guildID = guild.getId().asLong();

        if (!unavailable) {
            lembot.removeGuildStructure(guildID);
            dbHandler.removeGuild(guildID);
            lembot.getLogger().warn("Got kicked from guild {} with name {} and removed the structures", guildID, guild.getName());
        }
        else {
            lembot.getLogger().warn("Outage, can't connect to guild {} with name {}", guild, guild.getName());
        }
    }

    public void onGuildUpdate(Guild oldGuild, Guild newGuild) {
        // transfer ownership??
        dbHandler.addOwnerAsMaintainer(newGuild);
        lembot.getLogger().info("Guild {} with name {} has a new owner who was added as maintainer", newGuild.getId().asLong(), newGuild.getName());
    }

    public void onChannelDeleted(TextChannel channel) {
        Guild guild = channel.getGuild().block();
        Long guildID = guild.getId().asLong();
        GuildChannel defaultChannel = guild.getChannels().blockLast();

        GuildStructure guildStructure = lembot.provideGuildStructure(guildID);

        try {
            if (guildStructure.getAnnounce_channel().equals(channel.getId().asLong())) {
                guildStructure.setAnnounce_channel(defaultChannel.getId().asLong());
                dbHandler.setAnnounceChannel(guildID, defaultChannel.getId().asLong());
                lembot.sendMessage(defaultChannel, "My announcement channel #" + channel.getName() + " was deleted, so it was changed to <#" + defaultChannel.getId().asLong() + ">.");
                lembot.getLogger().info("Guild {} (id: {}) deleted the announcement channel so the default channel was set up", guild.getName(), guildID);
            }
        } catch (Exception e) {
            lembot.getLogger().warn("blubb", e);
        }

    }
}

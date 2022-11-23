package listeners;

import core.Lembot;

import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.event.domain.lifecycle.ResumeEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;

public class DiscordHandler {
    private Lembot lembot;

    public DiscordHandler(Lembot lembot) {
        this.lembot = lembot;
    }

    public void onDisconnected(DisconnectEvent event) {
        lembot.forceShutdown();
        lembot.getLogger().error("Discord client disconnected and announcers were shutdown");
    }

    public void onReconnected(ReconnectEvent event) {
        lembot.restartAfterOutage();
        lembot.getLogger().warn("Discord client reconnected and all announcers were restarted");
    }

    public void onReady(ReadyEvent event) {
        lembot.getLogger().info("Discord client is ready");
        lembot.getDiscordClient().updatePresence(Presence.online(Activity.playing("Grandfather III"))).subscribe();
        if (!lembot.isInitialized()) {
            lembot.init();
        }
    }

    public void onResumed(ResumeEvent event) {
        lembot.getLogger().info("The sessions of the Discord client were resumed");
        lembot.getDiscordClient().updatePresence(Presence.online(Activity.playing("Grandfather III"))).subscribe();
    }
}

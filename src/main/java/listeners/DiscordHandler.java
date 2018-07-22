package listeners;

import core.Lembot;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.shard.DisconnectedEvent;
import sx.blah.discord.handle.impl.events.shard.ReconnectSuccessEvent;
import sx.blah.discord.handle.impl.events.shard.LoginEvent;
import sx.blah.discord.handle.impl.events.shard.ResumedEvent;
import sx.blah.discord.handle.obj.ActivityType;
import sx.blah.discord.handle.obj.StatusType;

public class DiscordHandler {
    @EventSubscriber
    public void onDisconnected(DisconnectedEvent event) {
        Lembot.forceShutdown();
        Lembot.getLogger().error("Discord client disconnected and announcers were shutdown");
    }

    @EventSubscriber
    public void onReconnected(ReconnectSuccessEvent event) {
        Lembot.restartAfterOutage();
        Lembot.getLogger().warn("Discord client reconnected and all announcers were restarted");

    }

    @EventSubscriber
    public void onLogin(LoginEvent event) {
        Lembot.getDiscordClient().changePresence(StatusType.ONLINE, ActivityType.PLAYING, "Grandfather III");
        Lembot.getLogger().info("Discord client is logged in");
    }

    @EventSubscriber
    public void onResumed(ResumedEvent event) {
        Lembot.getLogger().info("The sessions of the Discord client were resumed");
    }
}

package listeners;

import core.Lembot;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.shard.DisconnectedEvent;
import sx.blah.discord.handle.impl.events.shard.ReconnectSuccessEvent;
import sx.blah.discord.handle.impl.events.shard.LoginEvent;
import sx.blah.discord.handle.impl.events.shard.ResumedEvent;
import sx.blah.discord.handle.obj.ActivityType;
import sx.blah.discord.handle.obj.StatusType;

public class DiscordHandler {
    private Lembot lembot;

    public DiscordHandler(Lembot lembot) {
        this.lembot = lembot;
    }

    @EventSubscriber
    public void onDisconnected(DisconnectedEvent event) {
        lembot.forceShutdown();
        lembot.getLogger().error("Discord client disconnected and announcers were shutdown");
    }

    @EventSubscriber
    public void onReconnected(ReconnectSuccessEvent event) {
        lembot.restartAfterOutage();
        lembot.getLogger().warn("Discord client reconnected and all announcers were restarted");
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        lembot.getLogger().info("Discord client is ready");
        lembot.init();
    }

    @EventSubscriber
    public void onLogin(LoginEvent event) {
        lembot.getDiscordClient().changePresence(StatusType.ONLINE, ActivityType.PLAYING, "Grandfather III");
        lembot.getLogger().info("Discord client is logged in");
    }

    @EventSubscriber
    public void onResumed(ResumedEvent event) {
        lembot.getLogger().info("The sessions of the Discord client were resumed");
    }
}

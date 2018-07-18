package listeners;

import core.Lembot;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.shard.DisconnectedEvent;
import sx.blah.discord.handle.obj.ActivityType;
import sx.blah.discord.handle.obj.StatusType;

public class DiscordHandler {
    @EventSubscriber
    public void onReady(ReadyEvent event) {
        Lembot.getDiscordClient().changePresence(StatusType.ONLINE, ActivityType.PLAYING, "Grandfather III");
    }

    @EventSubscriber
    public void onDisconnected(DisconnectedEvent event) {
        System.out.println("disconnected");
        while (!Lembot.getDiscordClient().isLoggedIn()) {
            Lembot.getDiscordClient().login();
        }
    }
}

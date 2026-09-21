package listeners;

import core.Lembot;

import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.lifecycle.ReconnectEvent;
import discord4j.core.event.domain.lifecycle.ResumeEvent;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;

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

        lembot.getLogger().info("=== DISCORD HANDLER ON READY ===");

        if (lembot.getGatewayDiscordClient() == null) {
            lembot.getLogger().error(
                    "GatewayDiscordClient is NULL in onReady()"
            );
            return;
        }

        lembot.getLogger().info(
                "Calling lembot.init()..."
        );

        lembot.init();

        lembot.getLogger().info(
                "lembot.init() returned"
        );
    }

    /**

    public void onReady(ReadyEvent event) {
        lembot.getLogger().info("=== READY EVENT RECEIVED ===");
        lembot.getLogger().info(
                "Bot initialized before init(): {}",
                lembot.isInitialized()
        );
        lembot.getLogger().info("Discord client is ready");

        lembot.getGatewayDiscordClient()
                .updatePresence(
                        ClientPresence.online(
                                ClientActivity.playing("Grandfather III")
                        )
                )
                .subscribe();

        if (!lembot.isInitialized()) {
            lembot.init();
        }
    } **/

    public void onResumed(ResumeEvent event) {
        lembot.getLogger().info("The sessions of the Discord client were resumed");

        lembot.getGatewayDiscordClient()
                .updatePresence(
                        ClientPresence.online(
                                ClientActivity.playing("Grandfather III")
                        )
                )
                .subscribe();
    }
}

package listeners;

import core.Lembot;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.event.connection.LostConnectionEvent;
import org.javacord.api.event.connection.ReconnectEvent;
import org.javacord.api.event.connection.ResumeEvent;
import org.javacord.api.listener.connection.LostConnectionListener;
import org.javacord.api.listener.connection.ReconnectListener;
import org.javacord.api.listener.connection.ResumeListener;

public class DiscordHandler implements LostConnectionListener, ReconnectListener, ResumeListener {
    private Lembot lembot;

    public DiscordHandler(Lembot lembot) {
        this.lembot = lembot;
    }

    @Override
    public void onLostConnection(LostConnectionEvent event) {
        lembot.forceShutdown();
        lembot.getLogger().error("Discord client disconnected and announcers were shutdown");
    }

    @Override
    public void onReconnect(ReconnectEvent event) {
        lembot.restartAfterOutage();
        lembot.getLogger().warn("Discord client reconnected and all announcers were restarted");
    }

    @Override
    public void onResume(ResumeEvent event) {
        lembot.getLogger().info("The sessions of the Discord client were resumed");
        lembot.getDiscordApi().updateActivity(ActivityType.PLAYING, "Grandfather III"); // online is missing
    }

    /*
    public void onReady(ReadyEvent event) {
        lembot.getLogger().info("Discord client is ready");
        lembot.getDiscordClient().updatePresence(Presence.online(Activity.playing("Grandfather III"))).subscribe();
        if (!lembot.isInitialized()) {
            lembot.init();
        }
    }
     */
}

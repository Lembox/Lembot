package listeners;

import commands.Commander;
import core.Lembot;
import discord4j.core.object.entity.Message;

public class MessageHandler {
    private Lembot lembot;
    private Commander commander;

    public MessageHandler(Lembot lembot) {
        this.lembot = lembot;
        commander = new Commander(lembot);
    }

    public void onMessageEvent(Message message) {
        lembot.getLogger().info("MESSAGE RECEIVED: {}", message.getContent());
        String prefix = "!";

        String content = message.getContent();

        if (content.startsWith(prefix)) {
            commander.processCommand(message, prefix);
        }
    }
}

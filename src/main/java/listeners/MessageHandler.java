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
        String prefix = "!";

        if (message.getContent().orElse("").toLowerCase().startsWith(prefix) || message.getContent().orElse("").toLowerCase().equals("!config")) {
            commander.processCommand(message, prefix);
        }
    }

}

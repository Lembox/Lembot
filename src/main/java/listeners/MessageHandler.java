package listeners;

import commands.Commander;

import core.Lembot;
import org.javacord.api.entity.message.Message;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

public class MessageHandler implements MessageCreateListener {
    private Lembot lembot;
    private Commander commander;

    public MessageHandler(Lembot lembot) {
        this.lembot = lembot;
        commander = new Commander(lembot);
    }
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        String prefix = "!";
        Message message = event.getMessage();

        if (message.getContent().toLowerCase().startsWith(prefix) || message.getContent().toLowerCase().equals("!config")) {
            commander.processCommand(message, prefix);
        }
    }

}

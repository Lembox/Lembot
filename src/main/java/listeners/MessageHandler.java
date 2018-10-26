package listeners;

import commands.Commander;

import core.Lembot;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;

public class MessageHandler {
    private Lembot lembot;
    private Commander commander;

    public MessageHandler(Lembot lembot) {
        this.lembot = lembot;
        commander = new Commander(lembot);
    }

    @EventSubscriber
    public void onMessageEvent(MessageReceivedEvent event) {
        String prefix = "!";
        IMessage message = event.getMessage();

        if (message.getContent().toLowerCase().startsWith(prefix) || message.getContent().toLowerCase().equals("!config")) {
            commander.processCommand(message, prefix);
        }
    }

}

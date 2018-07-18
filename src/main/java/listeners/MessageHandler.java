package listeners;

import commands.Commander;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

public class MessageHandler {
    String prefix = new String("!");

    @EventSubscriber
    public void onMessageEvent(MessageReceivedEvent event) {
        if (event.getMessage().getContent().toLowerCase().startsWith(prefix)) {
            Commander.processCommand(event.getMessage(), prefix);
        }
    }

}

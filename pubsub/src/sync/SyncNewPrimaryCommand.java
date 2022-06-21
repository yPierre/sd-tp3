package sync;

import core.Message;
import core.MessageImpl;
import core.PubSubCommand;

import java.util.Set;
import java.util.SortedSet;

public class SyncNewPrimaryCommand implements PubSubCommand {

    @Override
    public Message execute(Message msg, SortedSet<Message> log, Set<String> subscribers, boolean isPrimary, String secondaryAddress, int secondaryPort){

        Message response = new MessageImpl();
        response.setLogId(msg.getLogId());
        System.out.println(msg.getContent());
        response.setType("sync_primary_ack");

        return response;
    }
}

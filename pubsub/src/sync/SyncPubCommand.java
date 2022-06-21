package sync;

import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CopyOnWriteArrayList;

import core.Message;
import core.MessageImpl;
import core.PubSubCommand;
import core.client.Client;

public class SyncPubCommand implements PubSubCommand {

    @Override
    public Message execute(Message m, SortedSet<Message> log, Set<String> subscribers, boolean isPrimary, String sencondaryServerAddress, int secondaryServerPort) {

        Message response = new MessageImpl();
        int logId = m.getLogId();
        logId++;

        response.setLogId(m.getLogId());


        log.add(m);

        Message msg = new MessageImpl();
        msg.setContent(m.getContent());
        msg.setLogId(logId);
        msg.setType("notify");


        CopyOnWriteArrayList<String> subscribersCopy = new CopyOnWriteArrayList<String>();

        int inf = subscribers.size() / 2, sup = subscribers.size(), i = 0;
        System.out.println("Pub Backup broker");
        subscribersCopy.addAll(subscribers);
        for (String aux : subscribersCopy) {
            if(i >= inf && i < sup) {
                String[] ipAndPort = aux.split(":");
                Client client = new Client(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
                msg.setBrokerId(m.getBrokerId());
                Message cMsg = client.sendReceive(msg);
                if (cMsg == null) {
                    System.out.println("Notify of publish service is not proceed... " + msg.getContent());
                    subscribers.remove(aux);
                }
            }
        }

        response.setContent("Message published on backup: " + m.getContent());
        response.setType("pubsync_ack");

        return response;
    }

}

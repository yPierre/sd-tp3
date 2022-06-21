package sync;

import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;

import core.Message;
import core.MessageImpl;
import core.PubSubCommand;
import core.client.Client;

public class SyncSubCommand implements PubSubCommand {

    @Override
    public Message execute(Message m, SortedSet<Message> log, Set<String> subscribers, boolean isPrimary,
                           String sencondaryServerAddress, int secondaryServerPort) {
        Message response = new MessageImpl();

        if (subscribers.contains(m.getContent()))
            response.setContent("subscriber exists: " + m.getContent());
        else {

            response.setLogId(m.getLogId());

            subscribers.add(m.getContent());
            log.add(m);


            response.setContent("Subscriber added into backup: " + m.getContent());

            if (!log.isEmpty()) {
                // Codigo referente a tarefa de dividir o trabalho entre os brokers
                int inf = log.size() / 2, sup = log.size();
                System.out.println("--Sub Backup broker");

                Iterator<Message> it = log.iterator();
                String[] ipAndPort = m.getContent().split(":");
                while (it.hasNext() && inf < sup) {
                    Client client = new Client(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
                    Message msg = it.next();
                    Message aux = new MessageImpl();
                    aux.setType("notify");
                    aux.setContent(msg.getContent());
                    aux.setLogId(msg.getLogId());
                    aux.setBrokerId(m.getBrokerId());
                    Message cMsg = client.sendReceive(aux);
                    if (cMsg == null) {
                        subscribers.remove(m.getContent());
                        break;
                    }
                    inf++;
                }
            }

        }

        response.setType("subsync_ack");

        return response;
    }

}

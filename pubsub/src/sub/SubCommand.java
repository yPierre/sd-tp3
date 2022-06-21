package sub;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Set;
import java.util.SortedSet;

import core.Message;
import core.MessageImpl;
import core.PubSubCommand;
import core.client.Client;

public class SubCommand implements PubSubCommand {

    @Override
    public Message execute(Message m, SortedSet<Message> log, Set<String> subscribers, boolean isPrimary, String sencondaryServerAddress, int secondaryServerPort) {

        Message response = new MessageImpl();

        if (subscribers.contains(m.getContent()))
            response.setContent("subscriber exists: " + m.getContent());
        else {
            int logId = m.getLogId();
            logId++;

            response.setLogId(logId);
            m.setLogId(logId);

            if (sencondaryServerAddress != null && secondaryServerPort > 0) {
                try {
                    //sincronizar com o broker backup
                    Message syncSubMsg = new MessageImpl();
                    syncSubMsg.setBrokerId(m.getBrokerId());
                    syncSubMsg.setContent(m.getContent());
                    syncSubMsg.setLogId(m.getLogId());
                    syncSubMsg.setType("syncSub");

                    Client clientBackup = new Client(sencondaryServerAddress, secondaryServerPort);
                    syncSubMsg = clientBackup.sendReceive(syncSubMsg);
                    System.out.println(syncSubMsg.getContent());

                } catch (Exception e) {
                    System.out.println("Cannot sync with backup - subscribe service");
                }
            }

            subscribers.add(m.getContent());
            log.add(m);


            response.setContent("Subscriber added: " + m.getContent());

            //start many clients to send all existing log messages
            //for the subscribed user
            if (!log.isEmpty()) {
                /* Codigo referente a tarefa de dividir o trabalho entre os brokers
                int inf, sup;
                if(isPrimary) {
                    System.out.println("Primeiro trabalhou");
                    inf = 0;
                    sup = log.size() / 2;
                }
                else {
                    System.out.println("Backup trabalho!");
                    inf = log.size() / 2;
                    sup = log.size();
                }
                */
                Iterator<Message> it = log.iterator();
                String[] ipAndPort = m.getContent().split(":");
                while (it.hasNext()){// && inf < sup) {
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
                    //inf++;
                }
            }

        }

        response.setType("sub_ack");

        return response;

    }

}


package core;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;


//the useful socket consumer
public class PubSubConsumer<S extends Socket> extends GenericConsumer<S> {

    private int uniqueLogId;
    private SortedSet<Message> log;
    private Set<String> subscribers;
    private boolean isPrimary;
    private String secondaryServer;
    private int secondaryPort;

    public PubSubConsumer(GenericResource<S> re, boolean isPrimary, String secondaryServer, int secondaryPort) {
        super(re);
        uniqueLogId = 1;
        log = new TreeSet<Message>(new MessageComparator());
        subscribers = new TreeSet<String>();

        this.isPrimary = isPrimary;
        this.secondaryServer = secondaryServer;
        this.secondaryPort = secondaryPort;
    }


    @Override
    protected void doSomething(S str) {
        try {
            // TODO Auto-generated method stub
            ObjectInputStream in = new ObjectInputStream(str.getInputStream());

            Message msg = (Message) in.readObject();

            Message response = null;

            if(msg.getType().startsWith("syncNewPrimary")){
                if(!isPrimary) {
                    //System.out.println("Entrou syncNewPrimary");
                    //System.out.println(isPrimary + " - " + secondaryServer + " - " + secondaryPort);
                    response = commands.get(msg.getType()).execute(msg, log, subscribers, isPrimary, secondaryServer, secondaryPort);
                    this.isPrimary = true;
                    this.secondaryServer = null;
                    this.secondaryPort = 0;
                }
            }
            else if (!isPrimary && !msg.getType().startsWith("sync")) {
                response = new MessageImpl();
                response.setType("backup");
                response.setContent(secondaryServer + ":" + secondaryPort);
                System.out.println("O broker backup repassou ao broker primario");

            } else {
                if (!msg.getType().equals("notify") && !msg.getType().startsWith("sync")) {
                    //System.out.println("Ha um broker primario aqui");
                    msg.setLogId(uniqueLogId);
                }

                response = commands.get(msg.getType()).execute(msg, log, subscribers, isPrimary, secondaryServer, secondaryPort);

                if (!msg.getType().equals("notify"))
                    uniqueLogId = msg.getLogId();
            }

            ObjectOutputStream out = new ObjectOutputStream(str.getOutputStream());
            out.writeObject(response);
            out.flush();
            out.close();
            in.close();

            str.close();

        } catch (Exception e) {
            try {
                str.close();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

    }

    public List<Message> getMessages() {
        CopyOnWriteArrayList<Message> logCopy = new CopyOnWriteArrayList<Message>();
        logCopy.addAll(log);

        return logCopy;
    }

}

package appl;

import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import core.Message;
import core.MessageImpl;
import core.Server;
import core.client.Client;

public class PubSubClient {

    private Server observer;
    private ThreadWrapper clientThread;

    private String clientAddress;
    private int clientPort;

    protected String backupAddress;
    protected int backupPort;

    protected String primaryAddress;
    protected int primaryPort;

    public PubSubClient() {
        //this constructor must be called only when the method
        //startConsole is used
        //otherwise the other constructor must be called
    }

    public PubSubClient(String clientAddress, int clientPort) {
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        observer = new Server(clientPort);
        clientThread = new ThreadWrapper(observer);
        this.primaryAddress = null;
        this.primaryPort = 0;
        this.backupAddress = null;
        this.backupPort = 0;
        clientThread.start();
    }

    public void subscribe(String brokerAddress, int brokerPort, String brokerBackupAddress, int brokerBackupPort) {
        try {
            Message msgBroker = new MessageImpl();
            msgBroker.setBrokerId(brokerPort);
            msgBroker.setType("sub");
            msgBroker.setContent(clientAddress + ":" + clientPort);
            Client subscriber = new Client(brokerAddress, brokerPort);
            Message response = subscriber.sendReceive(msgBroker);
            if (response.getType().equals("backup")) {
                brokerAddress = response.getContent().split(":")[0];
                brokerPort = Integer.parseInt(response.getContent().split(":")[1]);
                subscriber = new Client(brokerAddress, brokerPort);
                subscriber.sendReceive(msgBroker);
            }
            primaryAddress = brokerAddress;
            primaryPort = brokerPort;
            backupAddress = brokerBackupAddress;
            backupPort = brokerBackupPort;
        } catch (Exception e){
            Message msgBroker = new MessageImpl();
            msgBroker.setBrokerId(brokerPort);
            msgBroker.setType("syncNewPrimary");
            msgBroker.setContent("Backup becomes primary: " + backupAddress + ":" + backupPort);

            Client clienteBackup = new Client(backupAddress, backupPort);
            clienteBackup.sendReceive(msgBroker);

            primaryAddress = backupAddress;
            primaryPort = backupPort;
            backupAddress = null;
            backupPort = -1;

            msgBroker = new MessageImpl();
            msgBroker.setBrokerId(backupPort);
            msgBroker.setType("sub");
            msgBroker.setContent(clientAddress + ":" + clientPort);
            Client subscriber = new Client(backupAddress, backupPort);
            Message response = subscriber.sendReceive(msgBroker);
            if (response.getType().equals("backup")) {
                brokerAddress = response.getContent().split(":")[0];
                brokerPort = Integer.parseInt(response.getContent().split(":")[1]);
                subscriber = new Client(brokerAddress, brokerPort);
                subscriber.sendReceive(msgBroker);
            }
        }

    }

    public void unsubscribe(String brokerAddress, int brokerPort) {

        Message msgBroker = new MessageImpl();
        msgBroker.setBrokerId(primaryPort);
        msgBroker.setType("unsub");
        msgBroker.setContent(clientAddress + ":" + clientPort);
        Client subscriber = new Client(primaryAddress, primaryPort);
        Message response = subscriber.sendReceive(msgBroker);

        if (response.getType().equals("backup")) {
            brokerAddress = response.getContent().split(":")[0];
            brokerPort = Integer.parseInt(response.getContent().split(":")[1]);
            subscriber = new Client(primaryAddress, primaryPort);
            subscriber.sendReceive(msgBroker);
        }
    }

    public void publish(String message, String brokerAddress, int brokerPort) {
        try {
            Message msgPub = new MessageImpl();
            msgPub.setBrokerId(primaryPort);
            msgPub.setType("pub");
            msgPub.setContent(message);

            Client publisher = new Client(primaryAddress, primaryPort);
            Message response = publisher.sendReceive(msgPub);

            if (response.getType().equals("backup")) {
                primaryAddress = response.getContent().split(":")[0];
                primaryPort = Integer.parseInt(response.getContent().split(":")[1]);
                publisher = new Client(primaryAddress, primaryPort);
                publisher.sendReceive(msgPub);
            }
        } catch (Exception e){
            Message msgBroker = new MessageImpl();
            msgBroker.setBrokerId(primaryPort);
            msgBroker.setContent("Backup promote to primary broker: " + backupAddress + ":" + backupPort);
            msgBroker.setType("syncNewPrimary");

            Client clientBackup = new Client(backupAddress, backupPort);
            clientBackup.sendReceive(msgBroker);

            primaryAddress = backupAddress;
            primaryPort = backupPort;

            Message msgPub = new MessageImpl();
            msgPub.setBrokerId(primaryPort);
            msgPub.setType("pub");
            msgPub.setContent(message);

            Client publisher = new Client(primaryAddress, primaryPort);
            Message response = publisher.sendReceive(msgPub);

            if (response != null) {
                if(response.getType().equals("backup")){
                    primaryAddress = response.getContent().split(":")[0];
                    primaryPort = Integer.parseInt(response.getContent().split(":")[1]);
                    publisher = new Client(primaryAddress, primaryPort);
                    publisher.sendReceive(msgPub);
                }
            }

        }


    }

    public List<Message> getLogMessages() {
        return observer.getLogMessages();
    }

    public void stopPubSubClient() {
        System.out.println("Client stopped...");
        observer.stop();
        clientThread.interrupt();
    }

    public void startConsole() {
        Scanner reader = new Scanner(System.in);  // Reading from System.in

        System.out.print("Enter the client port (ex.8080): ");
        int clientPort = reader.nextInt();
        System.out.println("Now you need to inform the broker credentials...");
        System.out.print("Enter the broker address (ex. localhost): ");
        String brokerAddress = reader.next();
        System.out.print("Enter the broker port (ex.8080): ");
        int brokerPort = reader.nextInt();

        observer = new Server(clientPort);
        clientThread = new ThreadWrapper(observer);
        clientThread.start();

        subscribe(brokerAddress, brokerPort, backupAddress, backupPort);

        System.out.println("Do you want to subscribe for more brokers? (Y|N)");
        String resp = reader.next();

        if (resp.equals("Y") || resp.equals("y")) {
            String message = "";

            while (!message.equals("exit")) {
                System.out.println("You must inform the broker credentials...");
                System.out.print("Enter the broker address (ex. localhost): ");
                brokerAddress = reader.next();
                System.out.print("Enter the broker port (ex.8080): ");
                brokerPort = reader.nextInt();
                subscribe(brokerAddress, brokerPort, backupAddress, backupPort);
                System.out.println(" Write exit to finish...");
                message = reader.next();
            }
        }

        System.out.println("Do you want to publish messages? (Y|N)");
        resp = reader.next();
        if (resp.equals("Y") || resp.equals("y")) {
            String message = "";

            while (!message.equals("exit")) {
                System.out.println("Enter a message (exit to finish submissions): ");
                message = reader.next();

                System.out.println("You must inform the broker credentials...");
                System.out.print("Enter the broker address (ex. localhost): ");
                brokerAddress = reader.next();
                System.out.print("Enter the broker port (ex.8080): ");
                brokerPort = reader.nextInt();

                publish(message, brokerAddress, brokerPort);

                List<Message> log = observer.getLogMessages();

                Iterator<Message> it = log.iterator();
                System.out.print("Log itens: ");
                while (it.hasNext()) {
                    Message aux = it.next();
                    System.out.print(aux.getContent() + aux.getLogId() + " | ");
                }
                System.out.println();

            }
        }

        System.out.print("Shutdown the client (Y|N)?: ");
        resp = reader.next();
        if (resp.equals("Y") || resp.equals("y")) {
            System.out.println("Client stopped...");
            observer.stop();
            clientThread.interrupt();

        }

        //once finished
        reader.close();
    }

    class ThreadWrapper extends Thread {
        Server s;

        public ThreadWrapper(Server s) {
            this.s = s;
        }

        public void run() {
            s.begin();
        }
    }

}

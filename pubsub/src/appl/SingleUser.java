package appl;

import core.Message;

import java.util.*;

public class SingleUser {


    public static void main(String[] args) {
        // TODO Auto-generated method stub
        new SingleUser();
    }

    public SingleUser(){
        Scanner reader = new Scanner(System.in);  // Reading from System.in
        System.out.print("Enter the Broker port number: ");
        int brokerPort = reader.nextInt();
        //int brokerPort = 8080;

        System.out.print("Enter the Broker address: ");
        String brokerAdd = reader.next();
        //String brokerAdd = "localhost";

        System.out.print("Enter the Broker backup port number: ");
        int brokerBackupPort = reader.nextInt();
        //int brokerBackupPort = 8081;

        System.out.print("Enter the Broker backup address: ");
        String brokerBackupAdd = reader.next();
        //String brokerBackupAdd = "localhost";

        System.out.print("Enter the User name: ");
        String userName = reader.next();

        System.out.print("Enter the User port number: ");
        int userPort = reader.nextInt();

        System.out.print("Enter the User address: ");
        String userAdd = reader.next();
        //String userAdd = "localhost";

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        PubSubClient user = new PubSubClient(userAdd, userPort);

        user.subscribe(brokerAdd, brokerPort, brokerBackupAdd, brokerBackupPort);
        //user.subscribe("localhost", 8081);

        startTP2(user, userName, brokerPort, brokerAdd);
    }

    //Protocolo
    //user1:access        - Solicita��o de acesso a SC
    //user1:request:User2 - User1 enviando request para o User2
    //user2:reply:User1   - User2 autorizando o request do User1
    //user3:reply:User1   - User3 autorizando o request do User1
    //User1:acquire       - User1 enviando um acquire(ele vai entrar na SC)
    //User1:release       - User1 saiu da SC

    private void startTP2 (PubSubClient user, String userName, int brokerPort, String brokerAdd){
        System.out.println("User " + userName + " entered the system!\n");
        String destination = null;

        Random seed = new Random();

        for(int i = 0; i < 100; i++){
            //fazendo um pub no broker

            //Solicita��o de quer entrar na SC
            Thread sendOneMsg = new ThreadWrapper(user, userName+":access:", brokerAdd, brokerPort);

            sendOneMsg.start();

            try{
                sendOneMsg.join();
                //Ap�s o access, � enviado um Request para todos os processos que est�o rodando
                List<Message> logUser = user.getLogMessages();
                List<String> receivers = listarUsuariosParaEnviarRequests(logUser, userName); //tem explica��o l� embaixo
                if(receivers.size() != 0) {
                    Iterator<String> it = receivers.iterator();
                    while(it.hasNext()){
                        sendOneMsg = new ThreadWrapper(user, userName + ":request:" + it.next(), brokerAdd, brokerPort);
                        sendOneMsg.start();
                        try {
                            sendOneMsg.join();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            //Verifica se tenho algum request para responder, caso haja, eles ser�o respondidos
            destination = precisoEnviarAlgumReply(user.getLogMessages(), userName); //Gera uma lista com o nome dos processos/usu�rios que ainda est�o esperando a resposta para o seus respectivos requests
            if(destination != null){
                String [] splitted = destination.split(":");
                for(int j = 0; j < splitted.length; j++) {
                    sendOneMsg = new ThreadWrapper(user, userName + ":reply:" + splitted[j], brokerAdd, brokerPort);
                    sendOneMsg.start();
                    try {
                        sendOneMsg.join();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            //Verifica se os requests que realizei j� foram respondidos
            List<Message> logUser = user.getLogMessages();
            while(!meusRequestsForamRespondidos(logUser, userName)) {
                Iterator<Message> it = logUser.iterator();
                try {
                    Thread.sleep(500);
                    System.out.println(".");
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                logUser = user.getLogMessages();
            }

            //CONTENT
            //Aqui acessa a SC, realizando o acquire e posteriormente o release
            sendOneMsg = new ThreadWrapper(user, userName + ":acquire", brokerAdd, brokerPort);
            sendOneMsg.start();

            try{
                sendOneMsg.join();
            }catch (Exception e){
                e.printStackTrace();
            }

            System.out.println("The resource " + "*" + " is available for you.");
            try {
                int wait = (int) ((Math.random() * (5000 - 1000)) + 3000);
                Thread.sleep(wait);
                System.out.println("The resource " + "*" + " was used for " + wait / 1000 + " seconds.");
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            System.out.println("User Release resource!");
            sendOneMsg = new ThreadWrapper(user, userName + ":release", brokerAdd, brokerPort);


            sendOneMsg.start();

            try{
                sendOneMsg.join();
            } catch (Exception e){
                e.printStackTrace();
            }

            //Printa o log do usu�rio
            Iterator<Message> it = logUser.iterator();
            System.out.print("Log User itens: ");
            while(it.hasNext()) {
                Message aux = it.next();
                System.out.print(aux.getContent() + " " + aux.getLogId() + " | ");
            }
            System.out.println();
        }

        user.unsubscribe(brokerAdd, brokerPort);
        user.stopPubSubClient();
    }

    //Lista os usu�rios que receber�o request, ou seja, todos os usu�rios do broker menos o que est� realizando o access naquele momento
    private List <String> listarUsuariosParaEnviarRequests(List<Message> logUser, String userName){
        List <String> usuarios = new ArrayList<>();
        Iterator<Message> it = logUser.iterator();
        while(it.hasNext()) { //Percorre o log pegando os nomes dos procesos/usu�rios
            Message aux = it.next();
            String[] splitted = aux.getContent().split(":");
            if(!usuarios.contains(splitted[0])){
                usuarios.add(splitted[0]);
            }
        }
        usuarios.remove(userName); //remove o nome do usu�rio que esta realizando o access naquele momento

        //usuarios.remove("35.199.69.5");
        //usuarios.remove("34.95.192.199");


        return usuarios;
    }

    //Verifica se h� algum request destinado ao usu�rio/processo que n�o foi respondido, caso haja, uma string com o nome dos usu�rios � retornada, caso n�o haja, � retornado null
    private String precisoEnviarAlgumReply(List<Message> logUser, String userName){
        String str = "";
        Iterator<Message> it = logUser.iterator();
        List <Message> requests = new ArrayList<Message>();
        List <Message> replies = new ArrayList<Message>();

        while (it.hasNext()){ //Conta a quantidade de requests e replies para o usu�rio/processo
            Message aux = it.next();
            if(aux.getContent().contains(":request:" + userName))
                requests.add(aux);
            else if(aux.getContent().contains(userName + ":reply:"))
                replies.add(aux);
        }

        if(requests.size() == replies.size())
            return null;
        else{
            Iterator<Message> reqIt = requests.iterator();
            for(int i = 0; i < requests.size(); i++) {
                Message aux = reqIt.next();
                if(i >= replies.size()) { //pega somente os processos que ainda n�o receberam reply
                    String[] splitted = aux.getContent().split(":");
                    str += (splitted[0] + ":");
                }
            }
            return str;
        }
    }

    //Verifica se todos os requests j� receberam reply e se pode dar acquire
    private boolean meusRequestsForamRespondidos(List<Message> logUser, String userName){
        Iterator<Message> it = logUser.iterator();
        int contRequest = 0, contReply = 0;

        while(it.hasNext()) {
            Message aux = it.next();
            if (aux.getContent().contains(userName + ":request:"))
                contRequest++;
            if(aux.getContent().contains("reply:" + userName))
                contReply++;
        }
        if(contRequest == contReply)
            return true;
        return false;
    }


    class ThreadWrapper extends Thread{
        PubSubClient c;
        String msg;
        String host;
        int port;

        public ThreadWrapper(PubSubClient c, String msg, String host, int port){
            this.c = c;
            this.msg = msg;
            this.host = host;
            this.port = port;
        }
        public void run(){
            c.publish(msg, host, port);
        }
    }
}

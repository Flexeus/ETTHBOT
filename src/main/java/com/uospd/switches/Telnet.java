package com.uospd.switches;


import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Telnet{
    private String login;
    private String pass;
    private InputStream inputStream;
    private Socket socket;
    private boolean authorized;
    private PrintWriter writer;
    private int authAttempts;
    private Commutator commutator;
    boolean enableCMDSent = false;

    public Telnet(String login, String password, Commutator commutator) throws Exception{
        this.login = login;
        this.pass = password;
        this.commutator = commutator;
        socket = new Socket();
    }

    public boolean connect(){
            try{
            socket.connect(new InetSocketAddress(commutator.getIp(), 23), 2000);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            inputStream = socket.getInputStream();
            writer = new PrintWriter(socket.getOutputStream());
            byte[] buffer = new byte[2056];
            int len;
            while((len = inputStream.read(buffer)) > 0){
                if(socket.isOutputShutdown()){
                    System.out.println("SOCKET ISN'T CONNECTED");
                    break;
                }
                String readchar = new String(buffer, 0, len);
                //System.out.print(readchar);
                if(!isAuthorized()){
                    if(authAttempts>5){
                        System.out.println("Не удалось авторизоваться "+commutator.getIp()+". Закрытие сокета");
                        socket.close();
                        break;
                    }
                    autoLogin(commutator, readchar);
                }
                if(readchar.trim().endsWith("#") || readchar.startsWith("%")){
                    //System.out.println("Авторизация на коммутаторе прошла");
                    authorized = true;
                    break;
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return authorized;
    }

    public boolean isAuthorized(){
        return authorized;
    }


    private void autoLogin(Commutator commutator, String readchar){

        if(enableCMDSent && readchar.contains("Password")){
            send("zorro");
            sleep();
        }

        if(readchar.contains("Username") || readchar.contains("Login:") || readchar.endsWith("login:".trim()) || readchar.startsWith("User") || readchar.startsWith("login") || readchar.trim().endsWith("ame:")){
            authAttempts++;
            send(login);
        }
        else if(readchar.contains("Password") || readchar.contains("word:") || readchar.startsWith("Pass")){
            authAttempts++;
            send(pass);
        }
        else if(readchar.endsWith(">")){
            authAttempts++;
            send("enable");
            sleep();
            enableCMDSent = true;
        }
        else if(readchar.contains(":user#") && commutator.model().getModel().equals("DES-3526")){
            authAttempts++;
            send("enable admin");
            sleep();
            send("");
        }
        else if(readchar.contains(":3#") || readchar.contains(":user#")){
            authAttempts++;
            send("enable admin");
            sleep();
            send("tgrad2013");
        }
    }

    public void close(){
        try{
            socket.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public String returnCMDResult() throws IOException{
        byte[] buffer = new byte[1056];
        inputStream.read(buffer);
        String s = new String(buffer);
        System.out.println(s);
        return s;
    }

    @SneakyThrows
    private void sleep(){
        Thread.sleep(150);
    }


    public void send(String ... commands){
        for(String s : commands){
            //System.out.println("Sending: " + s);
            writer.print(s + "\r\n");
            writer.flush();
            try{Thread.sleep(500);}catch(InterruptedException e){e.printStackTrace();}
        }
    }


}

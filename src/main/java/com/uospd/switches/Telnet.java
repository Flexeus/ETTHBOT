package com.uospd.switches;

import com.uospd.utils.Functions;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Telnet {
    private String login;
    private String pass;
    private volatile InputStream reader;
    private volatile Socket socket;
    private volatile boolean authorized;
    private PrintWriter writer;

    public Telnet(String login,String password,Commutator commutator, String... cmd){
        this(login,password,commutator, true, cmd);
    }


    public Telnet(String login,String password,Commutator commutator, boolean closeSocket, String... commands){
        this.login = login;
        this.pass = password;
        try{
            socket = new Socket(commutator.getIp(), 23);
            socket.setSoTimeout(7000);
            reader = socket.getInputStream();
            writer = new PrintWriter(socket.getOutputStream());
            byte[] buffer = new byte[1056];
            int len;
            loop:
            //   while(socket.isConnected()){
            while((len = reader.read(buffer)) > 0){
                if(!socket.isConnected()) break loop;
                String readchar = new String(buffer, 0, len);
                if(!authorized) autoLogin(commutator, readchar);
                if(readchar.trim().endsWith("#") || readchar.startsWith("%")){
                    System.out.println("Авторизация на коммутаторе прошла");
                    //System.out.print(readchar);
                    authorized = true;
                    for(String cmd : commands) sendcommand(cmd, true);
                    if(closeSocket){
                        Thread.sleep(2000);
                        socket.close();
                    }
                    break loop;
                }
                //  }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @SneakyThrows
    public Telnet(String login,String password,Commutator commutator){
        this.login = login;
        this.pass = password;
        socket = new Socket();
        socket.connect(new InetSocketAddress(commutator.getIp(),23 ),5000);
        reader = socket.getInputStream();
        writer = new PrintWriter(socket.getOutputStream());
        byte[] buffer = new byte[1056];
        int len;
        while((len = reader.read(buffer)) > 0){
            if(!socket.isConnected()) break;
            String readchar = new String(buffer, 0, len);
            if(!authorized) autoLogin(commutator, readchar);
            if(readchar.trim().endsWith("#") || readchar.startsWith("%")){
                System.out.println("Авторизация на коммутаторе прошла");
                //System.out.print(readchar);
                authorized = true;
                break;
            }
        }
    }

    private void autoLogin(Commutator commutator,String readchar) {
        if (readchar.contains("Username") || readchar.contains("Login:") || readchar.endsWith("login:".trim()) || readchar.startsWith("User") || readchar.startsWith("login") || readchar.trim().endsWith("ame:")) {
            sendcommand(login, false);
        }
        if (readchar.contains("Password") || readchar.contains("word:") || readchar.startsWith("Pass")) sendcommand(pass, false);
        if (readchar.endsWith(">")) sendcommand("enable", true);
        if (readchar.contains(":user#") && commutator.modelInfo().getModel().equals("DES-3526")) {
            sendcommand("enable admin", true);
            sendcommand("", false);
        }
        if (readchar.contains(":3#") || readchar.contains(":user#") ) {
            sendcommand("enable admin", true);
            sendcommand("tgrad2013", false);
        }
    }

    @SneakyThrows
    public String returnCMDResult() {
        byte[] buffer = new byte[4000];
        reader.read(buffer);
        return new String(buffer);
    }

    @SneakyThrows
    public void sendcommand(String command, boolean delay){
        Thread.sleep(100);
        writer.print(command + "\r\n");
        if(!command.contains(login)) System.out.println(Functions.getDate() + " Отправляем команду:" + command);
        writer.flush();
        if(delay) Thread.sleep(200);
    }

}

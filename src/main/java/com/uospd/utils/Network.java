package com.uospd.utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;


public class Network{
    public static String getAuthorization(String login){
        final String strtext = login.replace(".nvkz", ".Nvkz").replace("xdsl", "xDSL");
        String format = "http://92.124.239.7/cgi-bin/megasearch.cgi?city=nvkz&login="+strtext+"&bbar_ser=on";
        Document doc;
        Connection connection = Jsoup.connect(format).timeout(1000*16);
        try {
            doc = connection.get();
        } catch (IOException e) {
            e.printStackTrace();
            return "Произошла ошибка при проверке авторизации...";

        }

        Elements elements = doc.select("div[class=zagbig]") ;
        Elements elements2 = doc.select("div[class=zagmic]");
        elements.addAll(elements2);
        if (elements.size() == 0) return "Логин не авторизован";
        StringBuilder out = new StringBuilder();
        for (Element a : elements) {
            String str = a.text();
            if (str.contains("Routing Instance") || str.contains("Interface: ") || str.contains("IP Netmask:") || str.contains("IP Address Pool")  || str.contains("Session ID")  )
                continue;
            out.append(str).append("\n");
        }
        // out += "\n" + elements2.text();
        out = new StringBuilder(out.toString().replace("Redirect_IncPass", "Неправильный пароль").replace("Redirect_IncLogin", "Неправильный логин").replace("Redirect_IncPort", "Привязка. Неправильный порт")
                .replace("Redirect_IncUchet", "Неправильные учетные данные").replace("Redirect_NoInet", "Не куплен интернет. Моноуслуга IPTV/VOIP").replace("Logout", "").replace("Login Time:", "\nВремя авторизации:"));
        return out.toString();
    }

    public static boolean ping(String ip,int mills){
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(ip, 23), mills);
            sock.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

}

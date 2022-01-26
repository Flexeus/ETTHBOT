package com.uospd.controllers;

import com.uospd.Bot;
import com.uospd.annotations.AdminController;
import com.uospd.annotations.Command;
import com.uospd.entityes.User;
import com.uospd.services.CommutatorService;
import com.uospd.services.LoggingService;
import com.uospd.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.uospd.utils.Functions.*;

@AdminController
public class AdminCommandController{
    @Autowired private Bot bot;
    @Autowired private UserService userService;
    @Autowired private LoggingService logger;
    @Autowired private CommutatorService commutatorService;


    @Command(value = "/msg",description = "/msg [id] [text] - отправка сообщений пользователю")
    public void messageCommand(User user, String args[]){
        if (args.length == 1) { bot.sendMsg(user, "Не указан senderID"); return; }
        else if (args.length == 2) { bot.sendMsg(user, "Не указано сообщение"); return; }
        String text = String.join(" ", args).intern();
        int start = text.indexOf(args[2]);
        String msg = text.substring(start).intern();
        if (args[1].equals("all")) {
            userService.forEachUser((k, v) -> {
                bot.sendMsg(k, msg);
                try { Thread.sleep(200); } catch (InterruptedException e) { e.printStackTrace(); }
            });
        }
        else if(!isLong(args[1])){
            long id = userService.getIdByName(args[1]);
            if(id == -1){
                bot.sendMsg(user, "Пользователь не найден");
                return;
            }
            bot.sendMsg(id, msg);
        }
        else {
            Integer newsender = Integer.parseInt(args[1]);
            if (!userService.userExists(newsender)) {
                bot.sendMsg(user, "Такой пользователь не зарегистрирован");
                return;
            }
            bot.sendMsg(newsender, msg);
        }
    }

    @Command(value = "/log",description = "/log - получение файла лога\n/log [n] - вывод последних [n] сообщений из лога")
    public void showLogCommand(User user, String[] args){
        if(args.length == 1){ // просто выдаем лог файл
            bot.sendFile(user.getId(), logger.getMainLogFile());
            return;
        }
        if(!isInt(args[1])){ // если вводит не число, то это имя пользователя
            int id = userService.getIdByName(args[1]);
            if(id == -1){
                bot.sendMsg(user, "Пользователь не найден");
                return;
            }
            File logfile = logger.getUserLogFile(id);
            if(!logfile.exists()){
                bot.sendMsg(user, "Файл лога пользователя не найден");
                return;
            }
            bot.sendFile(user.getId(), logfile);
            return;
        }
        int arg = Integer.parseInt(args[1]);
        if(userService.userExists(arg)){
            File logfile = logger.getUserLogFile(arg);
            if(!logfile.exists()){
                bot.sendMsg(user, "Файл лога пользователя не найден");
                return;
            }
            bot.sendFile(user.getId(), logfile);
            return;
        }
        int linescount = Integer.parseInt(args[1]);
        Path path = logger.getMainLogFile().toPath();
        try{
            List<String> lines = Files.readAllLines(path, Charset.forName("CP1251"));
            String result;
            if(linescount > lines.size()) result = String.join("\n", lines).intern();
            else result = String.join("\n", lines.subList(lines.size() - linescount, lines.size()));
            bot.sendMsg(user, result);
        }catch(IOException e){
            e.printStackTrace();
            bot.sendMsg(user, "Не удалось вывести лог");
        }

    }

    @Command(value = "/t" ,description = "/t - отправка telnet команд на коммутатор")
    public void telnetCommand(User user, String[] args){
        String text = String.join(" ", args);
        String result = user.getSwitch().executeTelnetCommand(text.substring(text.indexOf(" ")).trim());
        bot.sendMsg(user, result);

    }

    @Command(value = "/showuser",description = "/showuser [id] - получить ссылку на профиль пользователя")
    public void showUser(User user, String[] args)
    {
        if(args.length == 1){
            bot.sendMsg(user, "Не указан userID");
            return;
        }
        long userid = Long.parseLong(args[1]);
        bot.sendMsgWithHTML(user.getId(), getAsLink("Click", "tg://user?id=" + userid));
    }


    @Command("/switches")
    public void switchesCommand(User user){
        bot.sendMsg(user, userService.getUsersConnections());
    }

    @Command(value = "/registered",description = "/registered - список зарегистрированных пользователей")
    public void registeredCommand(User user){
        bot.sendMsgWithHTML(user.getId(), userService.getRegisteredUsersList());
    }

    @Command(value = "/phones",description = "/phones - показать номера телефонов пользователей")
    public void phonesCommand(User user){
        bot.sendMsg(user, userService.getUsersPhones());
    }

    @Command(value = "/switchpool",description = "/switchpool - показать пул свитчей")
    public void switchpoolCommand(User user){
        bot.sendMsg(user, commutatorService.getSwitchPoolInfo());
    }
}

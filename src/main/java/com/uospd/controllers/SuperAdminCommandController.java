package com.uospd.controllers;

import com.uospd.Bot;
import com.uospd.ErrorsMonitor;
import com.uospd.UserNotFoundException;
import com.uospd.annotations.Command;
import com.uospd.annotations.SuperAdminController;
import com.uospd.entityes.User;
import com.uospd.services.CommutatorService;
import com.uospd.services.LoggingService;
import com.uospd.services.UserService;
import com.uospd.utils.KeyboardBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuperAdminController
public class SuperAdminCommandController{
    @Autowired(required = false) @Qualifier("emon")
    private ErrorsMonitor errorsMonitor;
    @Autowired private Bot bot;
    @Autowired private UserService userService;
    @Autowired private CommutatorService commutatorService;
    @Autowired private LoggingService logger;


    @Command(value = "/reg",description = "/reg - регистрация пользователя")
    public void registerCommand(User user){
        if(userService.getRegistrationRequests().isEmpty()){
            bot.sendMsg(user,"Список заявок на регистрацию пуст");
            return;
        }
        KeyboardBuilder kb = new KeyboardBuilder(2);
        userService.getRegistrationRequests().forEach( (id,newuser) -> {
            if(newuser.getPhoneNumber() != null){
                kb.addButtonOnRow("✔" + newuser.getName() + " | " + id, "reg;" + id);
                kb.nextRow();
                kb.addButtonOnRow("[X]", "declinereg;" + id);
            }
        });
        bot.sendMsg(user,"Выберите пользователя",kb.build());
    }

    @Command(value = "/elog",description = "/elog [n] - вывод [n] последних сообщений из лога с ошибками\n/elog - получение файла лога с ошибками")
    public void errorLogCommand(User user, String args[]){
        if(args.length == 1){
            bot.sendFile(user.getId(), logger.getErrorLogFile());
            return;
        }
        int linescount = Integer.parseInt(args[1]);
        Path path = logger.getErrorLogFile().toPath();
        try{
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String result = String.join("\n", lines.subList(lines.size() - linescount, lines.size()));
            bot.sendMsg(user, result);
        }catch(IOException e){
            e.printStackTrace();
        }

    }

    @Command(value = "/delacc",description = "/delacc - удаление аккаунта пользователя")
    public void deleteAccountCommand(User user, String args[]){
        if(args.length == 1){
            bot.sendMsg(user, "Не указан айди жертвы");
            return;
        }
        long banned = Long.parseLong(args[1]);
        if(!userService.userExists(banned)){
            bot.sendMsg(user, "Пользователь не найден");
            return;
        }
        try{
            userService.deleteUser(userService.getUser(banned));
        }catch(UserNotFoundException e){
            bot.sendMsg(user, "Не удалось удалить пользователя");
            return;
        }
        if(!userService.userExists(banned)) bot.sendMsg(user, "Пользователь был удален");
    }

    @Command(value = "/disconnectall",description = "/disconnectall - отсоединить всех пользователей от коммутаторов")
    public void disconnectAllCommand(User user){
        userService.getAllUsers().stream().filter(User::isConnectedToSwitch).forEach(u -> {
            commutatorService.disconnect(u.getSwitch());
            u.setCommutator(null);
        });
        bot.sendMsg(user,"Дело сделано");
    }

    @Command(value = "/close")
    public void closeCommand(){
        bot.onClosing();
        System.exit(0);
    }

    @Command(value = "/errormon")
    public void closeCommand(User user,String args[]){
        if(args.length == 1){
            bot.sendMsg(user, "Не указан тип действия: changestate, ignore");
            return;
        }
        String action = args[1];
        switch(action){
            case "changestate": errorsMonitor.changeState();
            case "ignore":{
                if(args.length == 2){
                    bot.sendMsg(user, "Не указан ip коммутатора");
                    return;
                }
                errorsMonitor.ignore(args[2]);
            }
            default: {
                bot.sendMsg(user, "Неизвестная подкоманда.");
            }
        }
    }
}

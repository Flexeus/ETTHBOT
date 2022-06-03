package com.uospd.controllers;

import com.uospd.Bot;
import com.uospd.annotations.BotController;
import com.uospd.annotations.Callback;
import com.uospd.entityes.Group;
import com.uospd.entityes.User;
import com.uospd.services.*;
import com.uospd.switches.Commutator;
import com.uospd.utils.*;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@BotController
public class CallbackController{
    @Autowired private Bot bot;
    @Autowired private UserService userService;
    @Autowired private ExecutorService botExecutor;
    @Autowired private LoggingService logger;
    @Autowired private CommutatorService commutatorService;
    @Autowired private AccidentService accidentService;
    @Autowired private AntifloodService antifloodService;

    @Callback
    public void reguser(User user, String[] args){
        System.out.println(Arrays.toString(args));
        long newUserId = Long.parseLong(args[0]);
        int newUserGroupId = Integer.parseInt(args[1]);
        Map<Long, User> registrationRequests = userService.getRegistrationRequests();
        User newUser = registrationRequests.get(newUserId);
        userService.registerUser(newUser, newUserGroupId);
        registrationRequests.remove(newUserId);
        bot.sendMsg(user, "Пользователь зарегистрирован");
        bot.sendMsg(newUser, "Ваша заявка на регистрацию была одобрена.");
        bot.sendCommandList(newUser);
    }

    @Callback
    public void reg(User user,String[] args){
        List<Group> groups = userService.getAllGroups();
        KeyboardBuilder keyboardBuilder = new KeyboardBuilder(3);
        for(int i=0;i<groups.size();i++){
            Group group = groups.get(i);
            keyboardBuilder.addButtonOnRow(group.getName(), String.format("reguser;%s;%d", args[0],group.getId()));
        }
        bot.sendMsg(user,"Выберите группу пользователя",keyboardBuilder.build());
    }

    @Callback
    public void declinereg(User user,String[] args){
        Map<Long, User> registrationRequests = userService.getRegistrationRequests();
        long declinedUser = Long.parseLong(args[0]);
        registrationRequests.remove(declinedUser);
        bot.sendMsg(user,"Заявка на регистрацию была отклонена");
        bot.sendMsg(declinedUser,"Заявка на регистрацию была отклонена администратором");
    }

    @Callback
    public void showswitches(User user, String[] args){
        String street = args[0];
        String house = args[1];
        bot.showSwitchList(user,commutatorService.getAllByAddress(street,house));
    }

    @Callback @SneakyThrows
    public void ping(User user, String args[]){
        String ip = args[0];
        for (int i = 0; i < 4; i++) {
            boolean result = Network.ping(ip,500);
            bot.sendMsg(user, "Ping:" + result);
            TimeUnit.SECONDS.sleep(1);
        }
    }

    @Callback
    public void ipadress(User user, String args[]){
        String ip = args[0];
        bot.connect(user, ip);
    }

    @Callback(timeout = 9)
    public void refreshportinfo(User user, String args[], CallbackQuery query) throws TimeoutException{
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        antifloodService.timeoutCheck("commutator:"+ip);
        int port = Integer.parseInt(args[1]);
        bot.deleteMessage(query.getMessage().getMessageId(),user.getId());
        bot.portCMD(user,port);
        antifloodService.action("commutator:"+ip,8);
        logger.writeUserLog(user, "обновил информацию о "+ip+":"+port);


    }

    @Callback(timeout = 120)
    public void restartport(User user, String args[]) throws TimeoutException{
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        antifloodService.timeoutCheck("restartport:"+ip);
        antifloodService.timeoutCheck("commutator:"+ip);
        int port = Integer.parseInt(args[1]);
        if (user.getSwitch().restartPort(port)){
            bot.sendMsg(user, "Порт был перезагружен");
            logger.writeUserLog(user,"Перезагрузил "+port+" порт");
        }
        else{
            bot.sendMsg(user, "Не удалось перезагрузить порт");
            logger.writeUserLog(user,"Не смог перезагрузить "+port+" порт");
        }
        antifloodService.action("restartport:"+ip,20);
        antifloodService.action("commutator:"+ip,8);
    }

    @Callback(timeout = 180)
    public void clearcounters(User user, String args[]) throws TimeoutException{
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }

        antifloodService.timeoutCheck("clearcounters:"+ip);
        antifloodService.timeoutCheck("commutator:"+ip);

        Commutator userSwitch = user.getSwitch();
        int port = Integer.parseInt(args[1]);
        final int olderrors = userSwitch.getErrorsCount(port);
        botExecutor.submit(() -> {
            try{
                userSwitch.dropCounters(port);
            }catch(Exception e){
                e.printStackTrace();
                bot.sendMsg(user,"Drop counters error");
            }
            try{ Thread.sleep(2000); }catch(InterruptedException e){ e.printStackTrace(); return; }
            int newerrors = userSwitch.getErrorsCount(port);
            if(newerrors < olderrors || newerrors == 0){
                bot.sendMsg(user, "Счетчик ошибок был сброшен");
                logger.writeUserLog(user.getId(), "Сбросил ошибки на " + userSwitch.getIp() + ":" + port);
            }
            else{
                bot.sendMsg(user, "Не удалось сбросить счетчик ошибок");
                logger.writeUserLog(user.getId(),"Не смог сбросить ошибки на " + userSwitch.getIp() + ":" + port);
                bot.SendToDebugChat("У пользователя " + user.getName() + " на " + userSwitch.getIp() + ":" + port + " не сбросились ошибки. NewErrors:" + newerrors);
            }
        });
        antifloodService.action("commutator:"+ip,8);
        antifloodService.action("clearcounters:"+ip,180);
    }

    @Callback(timeout = 180)
    public void showvlans(User user, String args[]) throws TimeoutException{
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        antifloodService.timeoutCheck("showvlans:"+ip);
        antifloodService.timeoutCheck("commutator:"+ip);
        int port = Integer.parseInt(args[1]);
        String vlans;
        try {
            vlans = user.getSwitch().showVlans(port);
        } catch (NullPointerException e) {
            bot.sendMsg(user, "Не удалось получить вланы");
            logger.writeUserLog(user.getId(),"Не смог посмотреть вланы на " + user.getSwitch().getIp() + ":" + port);
            return;
        }
        bot.sendMsg(user,vlans);
        logger.writeUserLog(user.getId(),"Посмотрел вланы на " + user.getSwitch().getIp() + ":" + port);
        antifloodService.action("commutator:"+ip,8);
        antifloodService.action("showvlans:"+ip,180);
    }

    @Callback(timeout = 30)
    public void showmacs(User user, String args[]) throws TimeoutException{
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        antifloodService.timeoutCheck("showmacs:"+ip);
        antifloodService.timeoutCheck("commutator:"+ip);
        int port = Integer.parseInt(args[1]);
        try {
            bot.sendMsg(user, user.getSwitch().getMacsOnPort(port));
            logger.writeUserLog(user.getId(),"Посмотрел маки на " + user.getSwitch().getIp() + ":" + port);
        } catch (NullPointerException e) {
            bot.sendMsg(user, "Не удалось получить маки");
            logger.writeUserLog(user.getId(),"Не смог посмотреть маки на " + user.getSwitch().getIp() + ":" + port);
        }
        antifloodService.action("showmacs:"+ip,30);
        antifloodService.action("commutator:"+ip,8);
    }

    @Callback(timeout = 20)
    public void ddm(User user, String args[]) throws TimeoutException{
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        if(!user.getSwitch().supportingDDM()) return;
        antifloodService.timeoutCheck("ddm:"+ip);
        antifloodService.timeoutCheck("commutator:"+ip);
        int port = Integer.parseInt(args[1]);
        bot.sendMsg(user, "Пожалуйста, подождите");
        String info = user.getSwitch().getDDMInfo(port);
        bot.sendMsg(user, info);
        logger.writeUserLog(user.getId(), "Посмотрел данные с SFP на " + user.getSwitch().getIp() + ":" + port);
        antifloodService.action("ddm:"+ip,20);
        antifloodService.action("commutator:"+ip,8);
    }

    @Callback
    public void updateAvarii(User user,String args[],CallbackQuery callbackQuery) throws TimeoutException{
        antifloodService.timeoutCheck("updateAvarii");
        int accidentMessageId = Integer.parseInt(args[0]);
        String accidentSwitches = accidentService.getDownSwitches();
        accidentSwitches = "Время обновления:"+ Functions.getDate()+"\n"+accidentSwitches;
        bot.editMessageText(user.getId(), accidentMessageId, accidentSwitches);
        int recoveredMessageId = callbackQuery.getMessage().getMessageId();
        bot.editMessageText(user.getId(), recoveredMessageId, accidentService.getUPSwitches());
        logger.writeUserLog(user, "обновил список аварий");
        antifloodService.action("updateAvarii",7);
    }

    @Callback
    public void station(User user,String args[]){
        String street = args[0];
        String home = args[1];
        List<Commutator> commutators = commutatorService.getAllByAddress(street, home);
        bot.sendMsg(user, street+" "+home);
        bot.showSwitchList(user, commutators);
    }
}

package com.uospd.controllers;

import com.uospd.Bot;
import com.uospd.annotations.BotController;
import com.uospd.annotations.Callback;
import com.uospd.entityes.Group;
import com.uospd.entityes.User;
import com.uospd.services.AccidentService;
import com.uospd.services.CommutatorService;
import com.uospd.services.LoggingService;
import com.uospd.services.UserService;
import com.uospd.switches.Commutator;
import com.uospd.utils.Functions;
import com.uospd.utils.KeyboardBuilder;
import com.uospd.utils.Network;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Callback("reguser")
    public void registerCallback(User user, String[] args){
        System.out.println(Arrays.toString(args));
        int newUserId = Integer.parseInt(args[0]);
        Integer newUserGroupId = Integer.parseInt(args[1]);
        String username = userService.getRegistrationRequests().get(newUserId);
        if(username == null) return;
        User newUser = new User(newUserId, username);
        userService.registerUser(newUser, newUserGroupId);
        bot.sendMsg(user, "Пользователь зарегистрирован");
        bot.sendMsg(newUser, "Ваша заявка на регистрацию была одобрена администратором");
        bot.sendCommandList(newUser);
    }

    @Callback("reg")
    public void userNameChoiceCallback(User user,String[] args){
        List<Group> groups = userService.getAllGroups();
        KeyboardBuilder keyboardBuilder = new KeyboardBuilder(3);
        for(int i=0;i<groups.size();i++){
            Group group = groups.get(i);
            keyboardBuilder.addButtonOnRow(group.getName(), String.format("reguser;%s;%d", args[0],group.getId()));
        }
        bot.sendMsg(user,"Выберите группу пользователя",keyboardBuilder.build());
    }

    @Callback("declinereg")
    public void declineRegistrationCallback(User user,String[] args){
        Map<Integer, String> registrationRequests = userService.getRegistrationRequests();
        long declinedUser = Long.parseLong(args[0]);
        registrationRequests.remove(declinedUser);
        bot.sendMsg(user,"Заявка на регистрацию была отклонена");
        bot.sendMsg(declinedUser,"Заявка на регистрацию была отклонена администратором");
    }

    @Callback("ping") @SneakyThrows
    public void pingButtonCallback(User user, String args[]){
        String ip = args[0];
        for (int i = 0; i < 4; i++) {
            boolean result = Network.ping(ip,500);
            bot.sendMsg(user, "Ping:" + result);
            TimeUnit.SECONDS.sleep(1);
        }
    }

    @Callback("ipadress")
    public void ipadressCallback(User user, String args[]){
        String ip = args[0];
        bot.connect(user, ip);
    }

    @Callback(value = "restartport",timeout = 120)
    public void restartPortCallback(User user, String args[]){
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        int port = Integer.parseInt(args[1]);
        if (user.getSwitch().restartPort(port)){
            bot.sendMsg(user, "Порт был перезагружен");
            logger.writeUserLog(user,"Перезагрузил "+port+" порт");
        }
        else{
            bot.sendMsg(user, "Не удалось перезагрузить порт");
            logger.writeUserLog(user,"Не смог перезагрузить "+port+" порт");
        }
    }

    @Callback(value = "clearcounters",timeout = 180)
    public void clearCountersCallback(User user, String args[]){
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        Commutator userSwitch = user.getSwitch();
        int port = Integer.parseInt(args[1]);
        final int olderrors = userSwitch.getErrorsCount(port);
        botExecutor.submit(() -> {
            userSwitch.dropCounters(port);
            try{ Thread.sleep(2000); }catch(InterruptedException e){ e.printStackTrace(); }
            int newerrors = userSwitch.getErrorsCount(port);
            if(newerrors < olderrors){
                bot.sendMsg(user, "Счетчик ошибок был сброшен");
                logger.writeUserLog(user.getId(), "Сбросил ошибки на " + userSwitch.getIp() + ":" + port);
            }
            else{
                bot.sendMsg(user, "Не удалось сбросить счетчик ошибок");
                logger.writeUserLog(user.getId(),"Не смог сбросить ошибки на " + userSwitch.getIp() + ":" + port);
                bot.SendToDebugChat("У пользователя " + user.getName() + " на " + userSwitch.getIp() + ":" + port + " не сбросились ошибки. NewErrors:" + newerrors);
            }
        });
    }

    @Callback(value = "showvlans",timeout = 180)
    public void showvlansCallback(User user, String args[]){
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
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
    }

    @Callback(value = "showmacs",timeout = 60)
    public void showMacsCallback(User user, String args[]){
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        int port = Integer.parseInt(args[1]);
        try {
            bot.sendMsg(user, user.getSwitch().getMacsOnPort(port));
            logger.writeUserLog(user.getId(),"Посмотрел маки на " + user.getSwitch().getIp() + ":" + port);
        } catch (NullPointerException e) {
            bot.sendMsg(user, "Не удалось получить маки");
            logger.writeUserLog(user.getId(),"Не смог посмотреть маки на " + user.getSwitch().getIp() + ":" + port);
        }
    }

    @Callback(value = "ddm",timeout = 20)
    public void ddmCallback(User user, String args[]){
        String ip = args[0];
        if (!user.isConnectedToSwitch() || !user.getSwitch().getIp().equals(ip)) {
            bot.sendMsg(user, "Вы не подключены к этому коммутатору");
            return;
        }
        if(!user.getSwitch().supportingDDM()) return;
        int port = Integer.parseInt(args[1]);
        bot.sendMsg(user, "Пожалуйста, подождите");
        String info = user.getSwitch().getDDMInfo(port);
        bot.sendMsg(user, info);
        logger.writeUserLog(user.getId(), "Посмотрел данные с SFP на " + user.getSwitch().getIp() + ":" + port);
    }

    @Callback(value = "updateAvarii",timeout = 5)
    public void updateAvariiCallback(User user,String args[]){
        int accidentMessageId = Integer.parseInt(args[0]);
        String accidentSwitches = accidentService.getDownSwitches();
        accidentSwitches = "Время обновления:"+ Functions.getDate()+"\n"+accidentSwitches;
        bot.editMessageText(user.getId(), accidentMessageId, accidentSwitches);
        int recoveredMessageId = Integer.parseInt(args[1]);
        bot.editMessageText(user.getId(), recoveredMessageId, accidentService.getUPSwitches());
        logger.writeUserLog(user, "обновил список аварий");
    }

    @Callback(value = "station",timeout = 5)
    public void stationCAllback(User user,String args[]){
        String street = args[0];
        String home = args[1];
        List<Commutator> commutators = commutatorService.getAllByStreetAndHome(street, home);
        bot.sendMsg(user, street+" "+home);
        bot.showSwitchList(user, commutators);
    }
}

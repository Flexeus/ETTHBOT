package com.uospd.controllers;

import com.uospd.Bot;
import com.uospd.annotations.BotController;
import com.uospd.annotations.Command;
import com.uospd.entityes.Station;
import com.uospd.entityes.User;
import com.uospd.services.AccidentService;
import com.uospd.services.CommutatorService;
import com.uospd.switches.Commutator;
import com.uospd.services.AntifloodService;
import com.uospd.utils.KeyboardBuilder;
import com.uospd.utils.Network;
import com.uospd.utils.TimeoutException;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@BotController
public class ControllerCMD{
    @Autowired private CommutatorService commutatorService;
    @Autowired private AccidentService accidentService;
    @Autowired private Bot bot;
    @Autowired private ExecutorService botExecutor;
    @Autowired private AntifloodService antifloodService;

    @Command({"/help","/start"})
    public void helpCommand(User user){
        bot.sendCommandList(user);
    }

    @Command(value = {"/auth"}, description = "/auth - проверка авторизации")
    public void checkAuthorizationCMD(User user, String args[]){
        if(args.length == 1){
            bot.sendMsg(user,"Не указан логин");
            return;
        }
        else if(args.length > 2){
            bot.sendMsg(user,"Неверный формат команды.");
            return;
        }
        String login = args[1];
        if (login.equals("cpdtest.nvkz")) {
            bot.sendMsg(user, "Это служебный логин");
            return;
        }
        antifloodService.action("auth:"+login,10);
        botExecutor.submit(() -> {
            String authorizationMessage = Network.getAuthorization(login+"@kem");
            if(authorizationMessage.equals("Логин не авторизован")) authorizationMessage = Network.getAuthorization(login);
            bot.sendMsg(user, authorizationMessage);
        });
    }


    @Command(value = {"/status","/статус","показать статус портов"},description = "/status - показать статус всех портов на коммутаторе")
    public void showPortsStatusCommand(User user) throws TimeoutException{
        if (!user.isConnectedToSwitch()) {
            bot.sendMsg(user, "Вы не подключены к коммутатору\nДля подключения к коммутатору введите его ip или адрес в чат");
            return;
        }
        Commutator commutator = user.getSwitch();
        antifloodService.timeoutCheck("commutator:"+commutator.getIp());
        boolean ping = commutator.ping(300);
        if(!ping){
            bot.sendMsg(user,"Нет пинга. Вы были отключены от коммутатора.");
            commutatorService.disconnect(commutator);
            user.setCommutator(null);
            return;
        }
        bot.sendMsg(user,commutator.getPortsStatus());
        antifloodService.action("commutator:"+commutator.getIp(),8);
    }

    @Command(value = "/clink",description = "/clink - проверка на подъем/падение линка на всех портах")
    public void clinkCommand(User user) throws TimeoutException{
        if(!user.isConnectedToSwitch()){
            bot.sendMsg(user, "Вы не подключены к коммутатору");
            return;
        }
        antifloodService.timeoutCheck("commutator:"+user.getSwitch().getIp());
        botExecutor.submit(() -> {
            int[] before = user.getSwitch().getAllLinks();
            bot.sendMsg(user, "Таймер запущен на 8 секунд.");
            try{ Thread.sleep(8000); }catch(InterruptedException e){ e.printStackTrace(); }
            int[] after = user.getSwitch().getAllLinks();
            int chan = 0;
            StringBuilder finalmsg = new StringBuilder();
            for(int i = 0;i < after.length;i++){
                if(before[i] == after[i]) continue;
                chan = 1;
                if(before[i] == 1) finalmsg.append("Пропал линк на ").append(i + 1).append(" порту\n");
                else finalmsg.append("Поднялся линк на ").append(i + 1).append(" порту\n");
            }
            if(chan == 0) finalmsg = new StringBuilder("Статус линка на всех портах остался прежним");
            bot.sendMsg(user, finalmsg.toString());
        });
        antifloodService.action("commutator:"+user.getSwitch().getIp(),20);
    }

    @SneakyThrows
    @Command(value = {"/ping"},description = "/ping [ip] - пинг по айпи")
    public void pingCommand(User user, String args[]){
        if(args.length == 1){
            bot.sendMsg(user,"Не указан ip коммутатора");
            return;
        }
        if(args.length == 2){
            if(user.isConnectedToSwitch() && user.getSwitch().getIp().equals(args[1]))
                commutatorService.disconnect(user.getSwitch());
            for(int i = 0;i < 4;i++){
                boolean result = Network.ping(args[1], 300);
                bot.sendMsg(user, "Ping:" + result);
                TimeUnit.SECONDS.sleep(1);
            }
        }
    }

    @Command(value = "/station",description = "/station - вывод списка станций",allowedMembers = {"УОСПД"})
    public void stationCMD(User user){
        KeyboardBuilder kb = new KeyboardBuilder(4);
        List<Station> stations = commutatorService.getStations();
        stations.sort(Comparator.comparingInt(Station::getNumber));
        for(Station s : stations){
            kb.addButtonOnRow(s.getType()+" "+s.getNumber(), "station;"+s.getStreet()+";"+s.getHome());
        }
        bot.sendMsg(user,"Станции",kb.build());
    }

    @Command(value = "/аварии",description = "/аварии - вывод списка аварий",allowedMembers = {"УОСПД","ЛТЦ"})
    public void avarii(User user){
        Message downMessage = bot.sendMsg(user, accidentService.getDownSwitches());
        Message upMessage = bot.sendMsg(user, accidentService.getUPSwitches());
        InlineKeyboardMarkup keyboard = KeyboardBuilder.oneButtonKeyboard("Обновить", String.format("updateAvarii;%d", downMessage.getMessageId()));
        bot.editKeyboard(user.getId(), upMessage.getMessageId(), keyboard);
    }


}

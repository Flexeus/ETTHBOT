package com.uospd;

import com.uospd.entityes.User;
import com.uospd.services.CommutatorService;
import com.uospd.services.LoggingService;
import com.uospd.services.UserService;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.ConnectException;
import com.uospd.switches.exceptions.InvalidDatabaseOIDException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.exceptions.UnsupportedCommutatorException;
import com.uospd.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendContact;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.uospd.utils.Functions.*;
/**
 * Класс-обработчик поступающих к боту сообщений.
 */

public class Bot extends TelegramLongPollingBot {
    private static final int MAX_MESSAGE_LENGTH = 4096;

    @Value("${bot.antiflood.seconds}") private int ANTIFLOOD_SECONDS;
    @Value("${bot.antiflood.enabled}") private boolean ANTIFLOOD_ENABLED;

    private final String BOT_NAME;
    //private final String BOT_TESTAPI_KEY;
    private final String BOT_API_KEY;

    @Value("${rwcommunity}") private String rwCommunity;

    @Autowired private ExecutorService es;
    @Autowired private CommutatorService commutatorService;
    @Autowired private UserService userService;
    @Autowired private LoggingService logger;
    @Autowired private CommandInvoker commandInvoker;
    @Autowired private CallbackInvoker callbackInvoker;

    private final Map<Integer, ReplyKeyboard> keyboardStorage = new HashMap<>();

    public Bot(String botName,String apiKey){
        BOT_NAME = botName;
        BOT_API_KEY = apiKey;
    }

    @PostConstruct
    private void init(){
        logger.log("Бот был загружен");
    }

    public void onUpdateReceived(Update update){
        if(userService == null) try{ Thread.sleep(3000); }catch(InterruptedException e){ e.printStackTrace(); }
        if(update.hasCallbackQuery()){
            logger.debug(update.getCallbackQuery().getData());
            String calldata = update.getCallbackQuery().getData();
            int sender = update.getCallbackQuery().getFrom().getId();
            if(!userService.userExists(sender)) return;
            User user;
            try{
                user = userService.getUser(sender);
            }catch(UserNotFoundException e){
                logger.log(e.getMessage());
                return;
            }
            logger.debug(user);
            if(user.isBanned()) return;
            String[] args = calldata.split(";");
            if(callbackInvoker.isCallbackAvailable(args[0])){
                try{
                    String result = callbackInvoker.executeCallback(calldata, user);
                    sendMsg(sender, result);
                }catch(TimeoutException e){
                    sendMsg(user, "Таймаут. Осталось секунд: "+e.getRemainingSeconds());
                }
            }else throw new IllegalStateException("Unexpected callback value: " + calldata);
        }else if(update.hasMessage()){

         //   RestrictChatMember restrictChatMember = new RestrictChatMember();
            Message message = update.getMessage();
            if(message.hasContact()){ // Если пользователь отправил свой номер телефона
                User user;
                try{
                    user = userService.getUser(message.getContact().getUserID());
                }
                catch(UserNotFoundException e){
                    return;
                }
                if(user.getPhoneNumber() != null) return; // если за пользователем уже закреплен номер то дальше не идем
                if(!message.getContact().getFirstName().equals(message.getFrom().getFirstName())){ // Если имя в контакте не совпало с именем аккаунта отправителя
                    logger.writeUserLog(user, "отправил чужой контакт");
                    sendMsg(user,"Вы должны отправить свой контакт!");
                    return;
                }
                String phoneNumber = message.getContact().getPhoneNumber();
                logger.writeUserLog(user, "отправил свой номер телефона: " + phoneNumber);
                user.setPhoneNumber(phoneNumber);
                userService.saveUser(user);
                sendMsg(user, "Доступ разрешен");
                return;
            }
            if(!update.getMessage().hasText()) return;
            String text = message.getText().toLowerCase();

            Integer sender = message.getFrom().getId();
            long chatid = message.getChatId();

            boolean confmessage = false;
            if(chatid != sender){ // Сообщение из группового чата
                    if(!userService.userExists(sender)) return;
                    if(text.startsWith("@rtketthbot")){
                        text = text.replace("@rtketthbot","").trim();
                    }
                    else return;
            //    logger.log("Сообщение из группового чата: " + message.getChat().getTitle());
            }
            User user;
            try{
                user = userService.getUser(sender);
            }catch(UserNotFoundException e){
                Map<Integer, String> registationRequests = userService.getRegistrationRequests();
                String name = message.getFrom().getFirstName() + " " + message.getFrom().getLastName();
                logger.log(String.format("*Незарегистрированный* %s(%d): %s", name, sender, text));
                if(registationRequests.containsKey(sender)){
                    sendMsg(sender, "Вы уже отправляли заявку на регистарцию");
                    return;
                }
                if(name.contains("null")){
                    sendMsg(sender, "Неверный формат имени(не указано имя или фамилия в профиле)");
                    return;
                }
                sendToSuperAdmin("Пользователь " + name + "(" + sender + ") хочет зарегистрироваться");
                sendMsg(sender, "Заявка на регистрацию была отправлена администратору");
                registationRequests.put(sender, name);
                return;
            }
            if(user.isBanned()) return;
            logger.writeUserLog(sender, text);
            logger.debug(user);
            if(user.getPhoneNumber() == null){
                logger.writeUserLog(user, "У пользователя нет номера телефона, предлагаем ввести");
                sendGetContactButton(sender);
                return;
            }

            if(ANTIFLOOD_ENABLED && !user.isAdmin()){
                int cmddif = (int) getDateDiff(user.getLastCMDDate(),TimeUnit.SECONDS);
                if(cmddif < ANTIFLOOD_SECONDS && (user.getLastCMD().equals(text) || isInt(user.getLastCMD()) && isInt(text))){
                    Date date = user.getLastCMDDate();
                    date.setTime(date.getTime() + TimeUnit.SECONDS.toMillis((long) Math.abs(cmddif/1.8)));
                    var newTimeout = ANTIFLOOD_SECONDS - getDateDiff(date, TimeUnit.SECONDS);
                    sendMsg(sender, "Антифлуд(Осталось секунд:" + newTimeout + ")");
                    logger.writeUserLog(user.getId(), "получил предупреждение за флуд");
                    return;
                }
            }
            user.setLastCMD(text);
            String[] args = text.split(" ");
            if(commandInvoker.isCommandAvailable(args[0])){
                String result = commandInvoker.executeCommand(args[0], user, args);
                sendMsg(sender, result);
                return;
            }
            if(text.equals("показать статус портов")){
                commandInvoker.executeCommand("/status", user, args);
            }else if(isInt(text)){
                if(!user.isConnectedToSwitch()) {
                    sendMsg(sender, "Для выбора порта подключитесь к коммутатору.");
                    return;
                }
                portCMD(user, Integer.parseInt(text));
            }else if(text.startsWith(">")){
                try{
                    String ip = text.substring(text.indexOf(">") + 1).intern();
                    connect(user, InetAddress.getByName(ip).getHostAddress());
                }catch(UnknownHostException e){
                    sendMsg(sender, "Не удалось подключиться к коммутатору: неверный ДНС");
                }
            }else if(text.startsWith("10.42.") && !containsIllegals(text) && text.length() > 8){
                int doubledot = text.indexOf(":");
                if(doubledot != -1){
                    int port = Integer.parseInt(text.substring(doubledot + 1));
                    String ip = text.substring(0, doubledot).intern();
                    connect(user, ip);
                    if(user.isConnectedToSwitch() && user.getSwitch().getIp().equals(ip)) portCMD(user, port);
                }else connect(user, text);
            }else if(!text.startsWith("/")){
                if(text.startsWith("атс") || text.startsWith("умсд")){
                    if(args.length != 2){
                        sendMsg(user,"Не указан номер станции");
                        return;
                    }
                    if(!isInt(args[1])){
                        sendMsg(user,"Некорректный номер станции");
                        return;
                    }
                    stationSearchCMD(args[0], Integer.parseInt(args[1]), user);
                    return;
                }
                else if(text.endsWith("атс") || text.endsWith("умсд")){
                    if(!isInt(args[0])){
                        sendMsg(user,"Некорректный номер станции");
                        return;
                    }
                    stationSearchCMD(args[1], Integer.parseInt(args[0]), user);
                    return;
                }
                streetCMD(user, text);
            }
            else sendMsg(user,"Неизвестная команда");
        }
    }

    private void stationSearchCMD(String stationType,int stationId,User user){
        List<Commutator> allByStation = commutatorService.getAllByStation(stationType, stationId);
        showSwitchList(user,allByStation);
    }

    public void checkAuthorization(int userid, String text) {
        if (text.equals("cpdtest.nvkz")) {
            sendMsg(userid, "Это служебный логин");
            return;
        }
        es.submit(() -> {
            String authorizationMessage = Network.getAuthorization(text+"@kem");
            if(authorizationMessage.equals("Логин не авторизован")) authorizationMessage = Network.getAuthorization(text);
            sendMsg(userid, authorizationMessage);
        });
    }

    public void streetCMD(User user, String text){
        String house, street;
        if(!text.contains(" ")){
            sendMsg(user, "Вы не указали номер дома\nНапример: Курако 39");
            return;
        }
        int lastsp = text.lastIndexOf(" ") + 1;
        street = text.substring(0, lastsp - 1);
        if(street.length() < 4){
            sendMsg(user, "Слишком короткий адрес");
            return;
        }
        house = text.substring(lastsp);
        if(house.contains("/") && house.length() > 6 || !house.contains("/") && house.length() > 4){
            sendMsg(user, "Неверный формат адреса.");
            return;  // до 5 значений
        }
        if(house.equals("-")){
            return;
        }
        List<Commutator> commutators = commutatorService.getAllByStreetAndHome(street, house);
        showSwitchList(user, commutators);
    }

    public void portCMD(User user, int port){
        if(!user.isConnectedToSwitch()) return;
        if(!user.getSwitch().ping(400)){
            sendMsg(user, "Нет пинга. Вы были отключены от коммутатора.");
            commutatorService.disconnect(user.getSwitch());
            user.setCommutator(null);
            return;
        }
        Commutator commutator = user.getSwitch();
        if(port>commutator.modelInfo().getPortsCount()+commutator.modelInfo().getUpLinkCount() || port < 1){
            sendMsg(user, "Неверный номер порта");
            return;
        }
        if(commutator.isAUpLink(port) && !user.getGroup().canWatchUplinks() && !user.isAdmin()){
            sendMsg(user, "В доступен отказано!");
            return;
        }
        if(commutator.getPortState(port)==2){
            sendMsg(user, "Состояние: закрыт");
            return;
        }
        var keyboardBuilder = new KeyboardBuilder();
        if(commutator.getErrorsCount(port) > 0 && commutator.supportingDropCounters() && user.getGroup().canClearCounters()){
            keyboardBuilder.addButtonOnRow("Сброс ошибок", "clearcounters;" + user.getSwitch().getIp() + ";" + port);
            keyboardBuilder.nextRow();
        }
        keyboardBuilder.addButtonOnRow("Перезагрузка порта", "restartport;" + user.getSwitch().getIp() + ";" + port);
        keyboardBuilder.nextRow();
        if(commutator.isTrunkPort(port) && commutator.supportingDDM() && user.getGroup().canWatchUplinks())
            keyboardBuilder.addButtonOnRow("DDM", "ddm;" + commutator.getIp() + ";" + port);
        if(commutator.supportingShowVlans())
            keyboardBuilder.addButtonOnRow("Вланы", "showvlans;" + commutator.getIp() + ";" + port);
        if(commutator.portLinkStatus(port))
            keyboardBuilder.addButtonOnRow("Маки", "showmacs;" + commutator.getIp() + ";" + port);
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardBuilder.build();
        String portInfo = commutator.getPortInfo(port);
        sendMsg(user, portInfo, inlineKeyboardMarkup);
    }

    public void showSwitchList(User user, List<Commutator> commutators){
        if(commutators.isEmpty()){
            sendMsg(user, "Ничего не найдено");
            return;
        }
        if(commutators.size() > 15) commutators = commutators.subList(0,15);
        KeyboardBuilder keyboardBuilder = new KeyboardBuilder(1);
        for(Commutator commutator : commutators){
            String ip = commutator.getIp();
            if(!ip.startsWith("10.42")) continue;
            if(!user.getGroup().canConnectToAggregation() && commutator.modelInfo().isAgregation()) continue;
            StringBuilder message = new StringBuilder();
            int porch = commutator.getPorch();
            if(!Network.ping(ip, 270)) message.append("[OFF] ");
            message.append("IP:").append(ip);
            if(porch != 0) message.append(".Подъезд:").append(porch);
            message.append("(").append(commutator.getVertical()).append(")");
            if(commutator.modelInfo() != null ) message.append("\n\t| ").append(commutator.modelInfo().getModel());
            keyboardBuilder.addButtonOnRow(message.toString(), "ipadress;" + ip);
        }
        sendMsg(user, "Найдено коммутаторов: "+keyboardBuilder.getRowsCount(), keyboardBuilder.build());
        commutators.clear();
    }



    public boolean connect(User user, String ip) {
        if(user.isConnectedToSwitch() && user.getSwitch().getIp().equals(ip)) {
            sendMsg(user, "Вы уже подключены к этому коммутатору");
            return false;
        }
        Commutator commutator;
        try{
            commutator = commutatorService.connect(ip, rwCommunity);
            if(!user.isAdmin() && commutator.modelInfo().isAgregation() && !user.getGroup().canConnectToAggregation()){
                sendMsg(user, "В доступе отказано!");
                logger.writeUserLog(user, "не смог подключиться к коммутатору " + commutator.getIp() + ". Нет прав доступа");
                return false;
            }
            String info = commutatorService.getSwitchInfo(commutator);
            sendMsg(user, info);
        }
        catch (UnsupportedCommutatorException e){
            sendMsg(user,"Данный коммутатор не поддерживается ботом");
            return false;
        }
        catch(ConnectException e){
            if(e.getCause() instanceof NullPointerException || e.getCause() instanceof NoSnmpAnswerException) SendToDebugChat("Видимо на коммутаторе " + ip + " нет коммьюнити engforta-rw");
            sendMsg(user, "Не удалось подключиться к коммутатору...");
            return false;
        }
        catch(InvalidDatabaseOIDException e){
            sendToSuperAdmin("Указанный в базе данных ObjectID для коммутатора "+ip+" не соотвествует реальному");
            sendMsg(user, "Не удалось подключиться к коммутатору...");
            return false;
        }
        if(user.isConnectedToSwitch()) commutatorService.disconnect(user.getSwitch());
        user.setCommutator(commutator);
        sendTextKeyboard(user.getId(),"Для выбора порта введите его номер в чат(1-" + commutator.modelInfo().getPortsCount() + ").","Показать статус портов");
        logger.writeUserLog(user, "подключился к коммутатору " + ip);
        return true;
    }

    public boolean editMessageText(long sender, int messageId, String newText){
        EditMessageText new_message = new EditMessageText();
        new_message.setChatId(String.valueOf(sender));
        new_message.setMessageId(messageId);
        new_message.setText(newText);
        if(keyboardStorage.containsKey(messageId)) new_message.setReplyMarkup((InlineKeyboardMarkup) keyboardStorage.get(messageId));
        try{
            execute(new_message);
        }catch(TelegramApiException e){
            if(!e.getMessage().contains("Error editing message text"))  e.printStackTrace();
            return false;
        }
        return true;
    }

    public void editKeyboard(long sender, int messageId, InlineKeyboardMarkup keyboard){
        var editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setMessageId(messageId);
        editMessageReplyMarkup.setChatId(String.valueOf(sender));
        editMessageReplyMarkup.setReplyMarkup(keyboard);
        try{
            execute(editMessageReplyMarkup);
        }catch(TelegramApiException e){
            if(!e.getMessage().contains("Error editing message text"))  e.printStackTrace();
            return;
        }
        keyboardStorage.put(messageId,keyboard);
    }

    public void sendToAdmins(String text){
        userService.getAdminList().forEach(admin -> sendMsg(admin,text));
    }

    public void sendToSuperAdmin(String text){
        sendMsg(810937833L,text);
    }

    public void SendToDebugChat(String text){
        if(Main.isTestMode()) sendToSuperAdmin(text);
        else sendMsg(-391675258L, text);
    }

    public void SendToUOSPD(String text){
        if(Main.isTestMode()) sendToSuperAdmin(text);
        else sendMsg(-1001256854052L, text);
    }


    public Message sendMsg(User user, String text){
        return sendMsg(user.getId(),text);
    }

    public Message sendMsg(Long l, String text){
        return sendMsg(Long.toString(l),text);
    }

    public Message sendMsg(String recipient, String text) {
        if(text == null || text.isEmpty()) return null;
        if(text.length()>MAX_MESSAGE_LENGTH && text.length() < 10000){
            sendMsg(recipient,text.substring(0,MAX_MESSAGE_LENGTH));
            return sendMsg(recipient,text.substring(MAX_MESSAGE_LENGTH));
        }
        try {
            var sendMessage = SendMessage.builder().chatId(recipient).text(text).build();
            return execute(sendMessage);
        }
        catch(TelegramApiException e){
            try{
                logger.log("Не удалось отправить сообщение пользователю:" + userService.getUser(Integer.parseInt(recipient)).getName());
            }catch(UserNotFoundException userNotFoundException){
                userNotFoundException.printStackTrace();
            }
            e.printStackTrace();
            return null;
        }
    }

    public Message sendMsg(int recipient, String text) {
        if(text == null || text.isEmpty()) return null;
        if(text.length()>MAX_MESSAGE_LENGTH && text.length() < 10000){
            sendMsg(recipient,text.substring(0,MAX_MESSAGE_LENGTH));
            return sendMsg(recipient,text.substring(MAX_MESSAGE_LENGTH));
        }
        try {
            var sendMessage = SendMessage.builder().chatId(String.valueOf(recipient)).text(text).build();
            return execute(sendMessage);
        }
        catch(TelegramApiException e){
            try{
                logger.log("Не удалось отправить сообщение пользователю:" + userService.getUser(recipient).getName());
            }catch(UserNotFoundException userNotFoundException){
                userNotFoundException.printStackTrace();
            }
            e.printStackTrace();
            return null;
        }
    }


    public Message sendMsg(User user, String text, ReplyKeyboard keyboard) {
        return sendMsg(user.getId(), text, keyboard);
    }

    public Message sendMsg(long recipient, String text, ReplyKeyboard keyboard) {
        if(text == null || text.equals("")) return null;
        try {
            SendMessage send = new SendMessage();
            send.setChatId(String.valueOf(recipient));
            send.setText(text);
            send.enableHtml(false);
            send.setReplyMarkup(keyboard);
            Message message = execute(send);
            keyboardStorage.put(message.getMessageId(),keyboard);
            return message;
        }
        catch (TelegramApiException e) { e.printStackTrace(); }
        return null;
    }

    public Message sendMsgWithHTML(long recipient, String text) {
        if(text == null || text.equals("")) return null;
        try {
            SendMessage send = new SendMessage();
            send.setChatId(String.valueOf(recipient));
            send.setText(text);
            send.enableHtml(true);
            Message message = execute(send);
            return message;
        }
        catch (TelegramApiException e) { e.printStackTrace(); }
        return null;
    }

    public void sendFile(long recipient, File file) {
        try{
            SendDocument message = new SendDocument();
            message.setChatId(String.valueOf(recipient));
            InputFile inputFile = new InputFile();
            inputFile.setMedia(file);
            message.setDocument(inputFile);
            execute(message);
        }
        catch(Exception e){
            logger.log("Не удалось отправить файл");
            e.printStackTrace();
        }
    }

    public Message sendTextKeyboard(long recipient, String message, String...buttons){
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        List<KeyboardRow> keyboard = new ArrayList<>();
        for (String button : buttons) {
            KeyboardRow keyboardFirstRow = new KeyboardRow();
            keyboardFirstRow.add(new KeyboardButton(button));
            keyboard.add(keyboardFirstRow);
        }
        replyKeyboardMarkup.setKeyboard(keyboard);
        return sendMsg(recipient, message, replyKeyboardMarkup);
    }

    public void sendGetContactButton(long sender){
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow();
        replyKeyboardMarkup.setResizeKeyboard(false);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        KeyboardButton keyboardButton = new KeyboardButton("Отправить");
        keyboardButton.setRequestContact(true);
        keyboardRow.add(keyboardButton);
        keyboard.add(keyboardRow);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText("Для работы с ботом требуется предоставить свой контакт. Нажмите кнопку 'Отправить");
        sendMessage.setChatId(String.valueOf(sender));
        replyKeyboardMarkup.setKeyboard(keyboard);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        try{
            execute(sendMessage);
        }catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    public void sendContact(long sender,String number, String firstName,String lastName){
        SendContact sendContact = new SendContact();
        sendContact.setPhoneNumber(number);
        sendContact.setChatId(String.valueOf(sender));
        sendContact.setFirstName(firstName);
        sendContact.setLastName(lastName);
        try{
            execute(sendContact);
        }catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    public void sendCommandList(User user){
        sendMsg(user, "Как пользоваться ботом?"+
                "\nДля подключения к коммутатору введите его адрес или ip-адрес в чат."+
                "\nПосле подключения к коммутатору введите требуемый номер порта или команду /status для просмотра статуса всех портов на коммутаторе");
        String commandList = commandInvoker.getCommandList(user);
        sendMsg(user,commandList);
    }


    @Override public String getBotUsername(){
        return BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BOT_API_KEY;
    }

    @Scheduled(initialDelay = 60000*60*24, fixedRate = 60000*60*24)
    public void commutatorDisconnector(){
        System.out.println("Disconnecting all from switches");
        userService.forEachUser( (id,u) -> {
            if(u.isConnectedToSwitch()){
                commutatorService.disconnect(u.getCommutator());
                u.setCommutator(null);
            }
        });
    }
}
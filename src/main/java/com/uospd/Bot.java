package com.uospd;

import com.uospd.entityes.User;
import com.uospd.services.*;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.ConnectException;
import com.uospd.switches.exceptions.InvalidDatabaseOIDException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.exceptions.UnsupportedCommutatorException;
import com.uospd.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendContact;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
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
import java.util.concurrent.TimeUnit;

import static com.uospd.utils.Functions.*;
/**
 * Класс-обработчик поступающих к боту сообщений.
 */

public class Bot extends TelegramLongPollingBot {
    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final String BOT_NAME;
    private final String BOT_API_KEY;

    @Autowired private CommutatorService commutatorService;
    @Autowired private UserService userService;
    @Autowired private LoggingService logger;
    @Autowired private CommandInvoker commandInvoker;
    @Autowired private CallbackInvoker callbackInvoker;
    @Autowired private AntifloodService antifloodService;

    private final Map<Integer, ReplyKeyboard> keyboardStorage = new HashMap<>();
    private boolean loaded = false;

    public Bot(String botName,String apiKey){
        BOT_NAME = botName;
        BOT_API_KEY = apiKey;
    }

    @PostConstruct
    private void init(){
        sendToSuperAdmin("Бот загружен");
        logger.log("Бот был загружен");
        loaded = true;
    }

    public void onUpdateReceived(Update update){
        User user = null;
        try{
           // if(userService == null) try{Thread.sleep(3000);}catch(InterruptedException e){e.printStackTrace();}
            if(!loaded) return;
            if(update.hasCallbackQuery()){
                logger.debug(update.getCallbackQuery().getData());
                String calldata = update.getCallbackQuery().getData();
                long sender = update.getCallbackQuery().getFrom().getId();

                try{user = userService.getUser(sender);}catch(UserNotFoundException e){return;}
                logger.debug(user);
                String[] args = calldata.split(";");
                if(!callbackInvoker.isCallbackAvailable(args[0])) throw new IllegalStateException("Unexpected callback value: " + calldata);
                CallbackQuery callbackQuery = update.getCallbackQuery();
                callbackInvoker.executeCallback(calldata, callbackQuery, user);
            }else if(update.hasMessage()){
                long dateDiff = getDateDiff(new Date(TimeUnit.SECONDS.toMillis(update.getMessage().getDate())), new Date(), TimeUnit.MINUTES); // Узнаем сколько минут прошло с момента сообщения
                if(dateDiff > 40){
                    System.out.println("datediff>40");
                    return; // если прошло больше 40 минут то пропускаем сообщение
                }
                Message message = update.getMessage();
                long sender = message.getFrom().getId();
                if(message.hasContact()){ // Если пользователь отправил свой номер телефона
                    Map<Long, User> registrationRequests = userService.getRegistrationRequests();
                    if(userService.userExists(sender) || !registrationRequests.containsKey(sender)) return;
                    User newUser = registrationRequests.get(sender);
                    if(sender != message.getContact().getUserId()){
                        logger.log(newUser.getName() + " отправил чужой контакт");
                        sendMsg(sender, "Вы должны отправить свой контакт!");
                        return;
                    }
                    String phoneNumber = message.getContact().getPhoneNumber();
                    newUser.setPhoneNumber(phoneNumber);
                    sendToSuperAdmin(String.format("%s(%d)\nТелефон:%s\nХочет зарегистрироваться.", newUser.getName(), newUser.getId(), newUser.getPhoneNumber()));
                    sendMsg(sender, "Заявка на регистрацию была отправлена администратору.");
                    return;
                }
                if(!update.getMessage().hasText()) return;
                String text = message.getText();
                long chatid = message.getChatId();
                if(sender != chatid) return;
                try{
                    user = userService.getUser(sender);
                }catch(UserNotFoundException e){
                    Map<Long, User> registationRequests = userService.getRegistrationRequests();
                    String name = message.getFrom().getFirstName() + " " + message.getFrom().getLastName();
                    logger.log(String.format("*Незарегистрированный* %s(%d): %s", name, sender, text));
                    if(registationRequests.containsKey(sender)){
                        sendMsg(sender, "Вы уже отправляли заявку на регистрацию");
                        return;
                    }
                    if(name.contains("null")){
                        sendMsg(sender, "Неверный формат имени(не указано имя или фамилия в профиле)");
                        return;
                    }
                    sendGetContactButton(sender);
                    registationRequests.put(sender, new User(sender, name));
                    return;
                }
                if(user.isBanned()) return;
                logger.writeUserLog(sender, text);
                logger.debug(user);
                String[] args = text.split(" ");
                if(commandInvoker.isCommandAvailable(args[0])){
                    String result = commandInvoker.executeCommand(args[0], user, args);
                    sendMsg(sender, result);
                    return;
                }
                if(commandInvoker.isCommandAvailable(text)){
                    String result = commandInvoker.executeCommand(text, user, args);
                    sendMsg(sender, result);
                    return;
                }
                if(isInt(text)){
                    if(!user.isConnectedToSwitch()){
                        sendMsg(sender, "Для выбора порта подключитесь к коммутатору.");
                        return;
                    }
                    antifloodService.timeoutCheck("commutator:" + user.getSwitch().getIp());
                    portCMD(user, Integer.parseInt(text));
                    antifloodService.action("commutator:" + user.getSwitch().getIp(), 8);
                }else if(text.startsWith(">")){
                    try{
                        String ip = text.substring(text.indexOf(">") + 1);
                        connect(user, InetAddress.getByName(ip).getHostAddress());
                    }catch(UnknownHostException e){
                        sendMsg(sender, "Не удалось подключиться к коммутатору. Возможно неверный ДНС");
                    }
                }else if(text.startsWith("10.42.") && !containsIllegals(text) && text.length() > 8){
                    int doubledot = text.indexOf(":");
                    if(doubledot != -1){
                        int port = Integer.parseInt(text.substring(doubledot + 1));
                        String ip = text.substring(0, doubledot);
                        connect(user, ip);
                        if(user.isConnectedToSwitch() && user.getSwitch().getIp().equals(ip)) portCMD(user, port);
                    }else connect(user, text);
                }else if(!text.startsWith("/")){
                    if(text.startsWith("атс") || text.startsWith("умсд")){
                        if(args.length != 2){
                            sendMsg(user, "Не указан номер станции");
                            return;
                        }
                        if(!isInt(args[1])){
                            sendMsg(user, "Некорректный номер станции");
                            return;
                        }
                        stationSearchCMD(args[0], Integer.parseInt(args[1]), user);
                        return;
                    }else if(text.endsWith("атс") || text.endsWith("умсд")){
                        if(!isInt(args[0])){
                            sendMsg(user, "Некорректный номер станции");
                            return;
                        }
                        stationSearchCMD(args[1], Integer.parseInt(args[0]), user);
                        return;
                    }
                    searchCMD(user, text);
                }else sendMsg(user, "Неизвестная команда");
            }
        }catch(TimeoutException e){
            if(user != null && !user.isAdmin()){
                sendMsg(user, "Таймаут. Осталось секунд: " + e.getRemainingSeconds());
                logger.writeUserLog(user, "получил предупреждение за флуд");
            }
        }
    }

    private void stationSearchCMD(String stationType,int stationId,User user){
        List<Commutator> allByStation = commutatorService.findStation(stationType, stationId);
        showSwitchList(user,allByStation);
    }

    public void searchCMD(User user, String text){
        if(text.length() < 4 && !text.equalsIgnoreCase("Доз")){
            sendMsg(user, "Слишком короткий адрес");
            return;
        }
        List<Commutator> allByStreet = commutatorService.getAllByStreet(text);
        if(allByStreet.isEmpty()){
            sendMsg(user, "Совпадений не найдено");
            return;
        }
        Map<String, List<Commutator>> houseMap = new TreeMap<>(new HouseNumberComparator());
        for(Commutator x: allByStreet){
            if(houseMap.containsKey(x.getHome()) && houseMap.size()>1) continue; // если больше 1 ключа, то нет смысла заполнять остальныее листы коммутаторами
            List<Commutator> commutators = houseMap.getOrDefault(x.getHome(), new ArrayList<>(8));
            commutators.add(x);
            houseMap.put(x.getHome(), commutators);
        }
        if(houseMap.size() == 1){ // Если полный адрес всего 1 то показываем сразу коммутаторы
            List<Commutator> commutators = houseMap.values().stream().findFirst().get();
            showSwitchList(user, commutators);
            return;
        }
        if(houseMap.size() > 99){
            sendMsg(user, "Найдено слишком много совпадений. Уточните запрос");
            return;
        }
        KeyboardBuilder kb = new KeyboardBuilder(8);
        houseMap.forEach((k, v) -> kb.addButtonOnRow(k, "showswitches;" + text + ";" + k)); // shoswitches;Зорге;10
        sendMsg(user, "Адресов найдено: " + houseMap.size(), kb.build());
    }

    public void portCMD(User user, int port){
        if(!user.isConnectedToSwitch()) return;
        Commutator commutator = user.getSwitch();
        if(!commutator.ping(300)){
            sendMsg(user, "Нет пинга. Вы были отключены от коммутатора.");
            commutatorService.disconnect(commutator);
            user.setCommutator(null);
            return;
        }
        if(port>commutator.model().getPortsCount()+commutator.model().getUpLinkCount() || port < 1){
            sendMsg(user, "Неверный номер порта");
            return;
        }
        if(commutator.isAUpLink(port) && !user.getGroup().canWatchUplinks() && !user.isAdmin()){
            sendMsg(user, "В доступе отказано");
            return;
        }
        var keyboardBuilder = new KeyboardBuilder();
        if(commutator.getErrorsCount(port) > 0 && commutator.supportingDropCounters() && user.getGroup().canClearCounters()) keyboardBuilder.addButtonOnRow("Сброс ошибок", "clearcounters;" + user.getSwitch().getIp() + ";" + port).nextRow();
        keyboardBuilder.addButtonOnRow("Перезагрузка порта", "restartport;" + user.getSwitch().getIp() + ";" + port).nextRow();
        if(commutator.isTrunkPort(port) && commutator.supportingDDM() && user.getGroup().canWatchUplinks()) keyboardBuilder.addButtonOnRow("DDM", "ddm;" + commutator.getIp() + ";" + port);
        if(commutator.supportingShowVlans()) keyboardBuilder.addButtonOnRow("Вланы", "showvlans;" + commutator.getIp() + ";" + port);
        if(commutator.portLinkStatus(port)) keyboardBuilder.addButtonOnRow("Маки", "showmacs;" + commutator.getIp() + ";" + port);
        keyboardBuilder.addButtonNextRow("Обновить", String.format("refreshportinfo;%s;%d",user.getSwitch().getIp(),port));
        InlineKeyboardMarkup markup = keyboardBuilder.build();
        String portInfo = commutator.getPortInfo(port);
        sendMsg(user, portInfo, markup);
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
            if(!user.getGroup().canConnectToAggregation() && commutator.model().isAgregation()) continue;
            StringBuilder message = new StringBuilder();
            int porch = commutator.getPorch();
            if(!Network.ping(ip, 270)) message.append("[OFF] ");
            message.append(ip);
            if(porch != 0) message.append(".Подъезд:").append(porch);
            message.append("(").append(commutator.getVertical()).append(")");
            if(commutator.model() != null ) message.append("\n\t| ").append(commutator.model().getModel());
            keyboardBuilder.addButtonOnRow(message.toString(), "ipadress;" + ip);
        }
        sendMsg(user, "Найдено коммутаторов: "+ keyboardBuilder.getRowsCount(), keyboardBuilder.build());
    }

    public boolean connect(User user, String ip) {
        if(user.isConnectedToSwitch() && user.getSwitch().getIp().equals(ip)) {
            sendMsg(user, "Вы уже подключены к этому коммутатору");
            return false;
        }
        Commutator commutator;
        try{
            commutator = commutatorService.connect(ip);
            if(!user.isAdmin() && commutator.model().isAgregation() && !user.getGroup().canConnectToAggregation()){
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
            if(e.getCause() instanceof NullPointerException || e.getCause() instanceof NoSnmpAnswerException) sendToSuperAdmin("Видимо на коммутаторе " + ip + " нет коммьюнити engforta-rw");
            sendMsg(user, "Не удалось подключиться к коммутатору...");
            return false;
        }
        catch(InvalidDatabaseOIDException e){
            sendToSuperAdmin("Указанный в базе данных ObjectID для коммутатора "+ip+" не соотвествует реальному");
            sendMsg(user, "Не удалось подключиться к коммутатору: неверный objectid");
            return false;
        }
        catch(NoSnmpAnswerException e){
            e.printStackTrace();
            sendMsg(user, "Не удалось подключиться к коммутатору");
            sendToSuperAdmin("Произошел no SNMP ANSWER EXCEPTION ПРИ ПОПЫТКЕ ПОДКЛЮЧЕНИЯ:"+ip);
            return false;
        }
        if(user.isConnectedToSwitch()) commutatorService.disconnect(user.getSwitch());
        user.setCommutator(commutator);
        sendTextKeyboard(user.getId(),"Для выбора порта введите его номер в чат(1-" + commutator.model().getPortsCount() + ").","Показать статус портов");
        logger.writeUserLog(user, "подключился к коммутатору " + ip);
        return true;
    }

    public void sendCommandList(User user){
        sendMsg(user, "Как пользоваться ботом?"+
                "\nДля подключения к коммутатору введите его адрес или ip-адрес в чат."+
                "\nПосле подключения к коммутатору введите требуемый номер порта или команду /status для просмотра статуса всех портов на коммутаторе");
        String commandList = commandInvoker.getCommandList(user);
        sendMsg(user,commandList);
    }

    @Override
    public String getBotUsername(){
        return BOT_NAME;
    }

    @Override
    public String getBotToken() { return BOT_API_KEY; }

    @Scheduled(initialDelay = 60000*60*4, fixedRate = 60000*60*4) // Каждые 4 часа
    public void commutatorDisconnector(){
        System.out.println("Disconnecting all from switches");
        userService.forEachUser( (id,user) -> {
            if(user.isConnectedToSwitch()){
                commutatorService.disconnect(user.getCommutator());
                user.setCommutator(null);
            }
        });
    }

    public void deleteMessage(int messageId,long chatId){
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setMessageId(messageId);
        deleteMessage.setChatId(String.valueOf(chatId));
        try{
            execute(deleteMessage);
        }catch(TelegramApiException e){
            e.printStackTrace();
        }
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

    public Message sendToSuperAdmin(String text){
        return sendMsg(810937833L,text);
    }

    public void SendToDebugChat(String text){
        sendToSuperAdmin(text);
    }

    public Message SendToUOSPD(String text){
        if(Main.isTestMode()) return sendToSuperAdmin(text);
        else{
            return sendMsg(-1001256854052L, text);
        }
    }

    public Message sendMsg(User recipient, String text) {
        return sendMsg(recipient.getId(),text);
    }

    public Message sendMsg(long recipient, String text) {
        if(text == null || text.isEmpty()) throw new IllegalArgumentException();
        if(text.length()>MAX_MESSAGE_LENGTH && text.length() < 10000){
            sendMsg(recipient,text.substring(0,MAX_MESSAGE_LENGTH));
            return sendMsg(recipient,text.substring(MAX_MESSAGE_LENGTH));
        }
        try {
            var sendMessage = SendMessage.builder().chatId(String.valueOf(recipient)).text(text).build();
            return execute(sendMessage);
        }
        catch(TelegramApiException e){
            logger.log("Не удалось отправить сообщение пользователю:" + recipient);
            e.printStackTrace();
            return null;
        }
    }

    public Message sendMsg(User user, String text, ReplyKeyboard keyboard) {
        return sendMsg(user.getId(), text, keyboard);
    }

    public Message sendMsg(long recipient, String text, ReplyKeyboard keyboard) {
        if(text == null || text.equals("")) throw new IllegalArgumentException();
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
}
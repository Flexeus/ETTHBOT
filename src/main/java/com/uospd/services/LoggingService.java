package com.uospd.services;

import com.uospd.Main;
import com.uospd.UserNotFoundException;
import com.uospd.entityes.User;
import com.uospd.utils.DualStream;
import com.uospd.utils.Functions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class LoggingService {
    @Value("${bot.logger.directory.windows}")
    private String windowsDirectory;
    @Value("${bot.logger.directory.linux}")
    private String linuxDirectory;

    @Autowired private UserService userService;

    private String logdir;
    private String userslogdir;

    private File log;
    private File errorLog;
    private File connectionsLog;

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private String currentDate = dateFormat.format(new Date());
    private final Charset charset = Charset.forName("cp1251");
    private final String separator = File.separator;
    private final String lineSeparator = System.getProperty("line.separator");

    @PostConstruct
    private void init() {
        userslogdir= logdir+"users"+ separator;
        String os = System.getProperty("os.name").toLowerCase();
        logdir = (os.contains("win") ? windowsDirectory : linuxDirectory) + separator;
        userslogdir=logdir+"users"+separator;

        File logDirectory = new File(logdir);
        if (!logDirectory.exists()) logDirectory.mkdir();

        File userLogDirectory = new File(userslogdir);
        if (!userLogDirectory.exists()) userLogDirectory.mkdir();



        log = createLogFile(currentDate,logdir);
        errorLog = createLogFile("error",logdir);
        connectionsLog = createLogFile("connections",logdir);

        try{
            System.setErr(new DualStream(System.err, new PrintStream(new FileOutputStream(errorLog, true), true, charset)));
        }catch(FileNotFoundException e){
            e.printStackTrace();
        }
    }


    private File createLogFile(String name,String directory){
        File file = new File(directory + name + ".log");
        try{
            file.createNewFile();
        }catch(IOException e){
            e.printStackTrace();
        }
        return file;
    }

    public void writeUserLog(User user,String str){
        writeUserLog(user.getId(),str);
    }

    public void writeUserLog(Long id, String str) {
        User currentUser;
        try{
            currentUser = userService.getUser(id);
        }catch(UserNotFoundException e){
            this.log("Юзер с id="+id+" не находится в списке пользователей. Не логируем");
            return;
        }
        if(currentUser.isSuperAdmin() && !Main.isTestMode()) return;

        File userFile = createLogFile(String.valueOf(id),userslogdir);
        log(String.format("%s(%d): %s",currentUser.getName(),id,str));
        try(OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(userFile,true), charset)){
            osw.write(Functions.getDate() + " " + currentUser.getName() + ": " + str + lineSeparator);
            osw.flush();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public void log(String str) {
        String format = dateFormat.format(new Date());
        if(!format.equals(currentDate)){
            System.out.println("Изменился день:" + format);
            log = createLogFile(format, logdir);
            currentDate = format;
        }
        String message = String.format("%s %s", Functions.getTime(), str);
        System.out.println(message);
        try(var osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(log, true)), charset)){
            osw.write(message + lineSeparator);
            osw.flush();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void connectionsLog(String str){
        String message = String.format("%s %s", Functions.getTime(), str);
        try(var osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(connectionsLog, true)), charset)){
            osw.write(message + lineSeparator);
            osw.flush();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void debug(String str) {
        if(Main.isTestMode()) System.out.println("[DEBUG] "+str);
    }

    public void debug(Object obj) {
        debug(obj.toString());
    }

    public File getUserLogFile(Long id){
        return new File(userslogdir + id + ".log");
    }

    public File getMainLogFile(){
        return log;
    }

    public File getErrorLogFile(){
        return errorLog;
    }

}


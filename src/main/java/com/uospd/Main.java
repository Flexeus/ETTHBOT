package com.uospd;


import com.uospd.annotations.BotController;
import com.uospd.annotations.Callback;
import com.uospd.annotations.Command;
import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.interfaces.CommutatorStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Configuration
@ComponentScan
@PropertySource("classpath:application.properties")
@EnableJpaRepositories
@EnableScheduling
public class Main {
    private static boolean testMode = false;

    public static void main(String[] args){
        System.out.println("Starting...");
        Set<String> artifactoryLoggers = new HashSet<>(Arrays.asList(//"org.jboss.logging",
                "org.apache.http",
                //"groovyx.net.http",
                "org.hibernate",
                "org.springframework"
        ));
        for(String log:artifactoryLoggers) {
            ch.qos.logback.classic.Logger artLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(log);
            artLogger.setLevel(ch.qos.logback.classic.Level.INFO);
            artLogger.setAdditive(false);
        }

        for(String a:args){
            if(a.equals("--test")){
                testMode = true;
                System.out.println("Testmod enabled");
                break;
            }
        }
        new AnnotationConfigApplicationContext(Main.class);
    }


    @Bean
    public static Bot telegramBot(@Value("${bot.name}") String BOT_NAME, @Value("${bot.api_key}") String BOT_API_KEY, @Value("${bot.test_api_key}") String TEST_BOT_API_KEY) {
        try {
            Bot bot = new Bot(BOT_NAME,!testMode?BOT_API_KEY:TEST_BOT_API_KEY);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            return bot;
        } catch (TelegramApiException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    @Bean(name = "strategyMapBean")
    public static Map<String, CommutatorStrategy> commutatorStrategyMap(ApplicationContext context){
        Map<String,CommutatorStrategy> map = new HashMap<>();
        Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(CommutatorStrategyComponent.class);
        for(Object strategybean : beansWithAnnotation.values()){
            Class<?> valueClass = strategybean.getClass();
            CommutatorStrategyComponent annotation = valueClass.getAnnotation(CommutatorStrategyComponent.class);
            String[] oidsArray = annotation.value();
            for(String oid : oidsArray){
                map.put(oid,(CommutatorStrategy)strategybean);
            }
        }
        return map;
    }

    @Bean(name = "commandControllers")
    public static Map<String, Pair<Object,Method>> commandControllersBeans(ApplicationContext context){
        Map<String,Pair<Object,Method>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Object> beans = context.getBeansWithAnnotation(BotController.class);

        beans.forEach( (k,v) -> {
            Method[] methods = v.getClass().getMethods();
            for(Method method : methods){
                if(!method.isAnnotationPresent(Command.class)) continue;
                Command annotation = method.getAnnotation(Command.class);
                String[] value = annotation.value();
                Pair<Object, Method> of = Pair.of(v,method);
                for(String s : value) map.put(s, of);
            }
        });
        return map;
    }

    @Bean(name = "callbackControllers")
    public static Map<String, Pair<Object,Method>> callbackControllersBeans(ApplicationContext context){
        Map<String,Pair<Object,Method>> map = new HashMap<>();
        Map<String, Object> beans = context.getBeansWithAnnotation(BotController.class);
        beans.forEach( (k,v) -> {
            Method[] methods = v.getClass().getMethods();
            for(Method method : methods){
                if(!method.isAnnotationPresent(Callback.class)) continue;
                String value = method.getName();
                Pair<Object, Method> of = Pair.of(v,method);
                map.put(value, of);
            }
        });
        return map;
    }

    @Bean(name="botTaskExecutor")
    public static ExecutorService botTaskExecutor(){
        return Executors.newFixedThreadPool(2);
    }

    public static boolean isTestMode() {
        return testMode;
    }

}

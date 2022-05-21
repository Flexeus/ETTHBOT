package com.uospd;

import com.uospd.services.CommutatorService;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.InvalidDatabaseOIDException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.utils.Functions;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "bot.errorsmonitor.enabled", havingValue = "true")
public class ErrorsMonitor{

    @AllArgsConstructor @EqualsAndHashCode @ToString
    private static class PortInfo{
        private final String commutatorIP;
        private final int port;
    }

    @Autowired private CommutatorService commutatorService;
    @Autowired private Bot bot;

    private boolean activated = true;
    private List<Commutator> commutators;
    private final Map<PortInfo, Integer> errorsMap = new HashMap<>();
    private final Map<PortInfo, Integer> warningsMap = new HashMap<>();

    private final int CHECK_TIME = 60;// Частота проверок ошибок в минутах;
    private final int UNWARNING_TIME = 120; // Время для снятия ошибок в часах;

    @Value("${rwcommunity}")
    private String COMMUNITY;

    @Value("${bot.errorsmonitor.warn_errors_count}")
    private int WARN_ERRORS_COUNT;

    @PostConstruct
    public void init(){
        System.out.println("Мониторинг ошибок запущен");
        commutators = commutatorService.getAllCommutators();
    }

    @Scheduled(fixedDelay = UNWARNING_TIME * 60 * 60000,initialDelay = UNWARNING_TIME * 60 * 60000)
    public void secondTimer(){
        if(!activated) return;
        System.out.println("Снятие варнов...");
        warningsMap.entrySet().stream()
                //.filter(entry -> entry.getValue() == 4)
                .forEach(entry-> {
            entry.setValue(0);
            System.out.println("Варны сняты с "+entry.getKey().commutatorIP+":"+entry.getKey().port);
        });
    }

    @Scheduled(fixedDelay = CHECK_TIME * 60000,initialDelay =  3000)
    public void checkErrors(){
        if(!activated) return;
        System.out.println(Functions.getTime()+" Проверка на ошибки запущена");
        for(Commutator c : commutators){
            try{
                Commutator commutator = commutatorService.connect(c, COMMUNITY);
                int portsCount = commutator.model().getPortsCount()+commutator.model().getUpLinkCount();
                for(int i = 1;i <= portsCount;i++){
                    if(!commutator.isTrunkPort(i)) continue;
                    int errorsCount = commutator.getErrorsCount(i);
                    PortInfo portInfo = new PortInfo(commutator.getIp(), i);
                    if(errorsMap.containsKey(portInfo)){
                        Integer oldErrorsCount = errorsMap.get(portInfo);
                        if(errorsCount == 0 || errorsCount<oldErrorsCount) warningsMap.remove(portInfo); // если ошибки исчезли, но были варнинги, то убираем их
                        if(errorsCount-oldErrorsCount >= WARN_ERRORS_COUNT){ // если с момента прошлой проверки накопилось n ошибок
                            int warningCount = warningsMap.getOrDefault(portInfo, 0)+1;
                            warningsMap.put(portInfo, warningCount); // то накапливаем варнинги, чтобы убедиться, что рост ошибок постоянен
                            if(warningCount == 3) // если накопилось 3 варнинга, то ошибки точно копятся постоянно - отсылаем сообщение
                                bot.SendToUOSPD(String.format("""
                                                Фиксируется накопление ошибок на %s:%d
                                                "Hostname: %s
                                                "Ошибок: %d
                                                "Накопилось за %d минут: %d""", commutator.getIp(), i, commutator.getHostName(),
                                        errorsCount, CHECK_TIME, errorsCount-oldErrorsCount));
                        }
                    }
                    errorsMap.put(portInfo,errorsCount);
                }
                commutatorService.disconnect(commutator);
            }
            catch(InvalidDatabaseOIDException | NoSnmpAnswerException e){ e.printStackTrace(); }
            catch(Exception ignored){ }

        }
        System.out.println(Functions.getTime()+" Проверка на ошибки завершена");
    }

    public void ignore(String ip){
        commutators.removeIf(x->x.getIp().equals(ip));
    }


    public void changeState(){
        activated = !activated;
    }
}

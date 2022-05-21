package com.uospd;

import com.uospd.services.CommutatorService;
import com.uospd.switches.Commutator;
import com.uospd.switches.interfaces.CommutatorStrategy;
import com.uospd.utils.Functions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class CommunityDeleter{

    @Autowired @Qualifier("strategyMapBean")
    private final Map<String, CommutatorStrategy> strategyMap= null;

    @Autowired private CommutatorService commutatorService;

    @Value("${telnet.login}")
    private String telnetLogin;
    @Value("${telnet.password}")
    private String telnetPassword;

    private List<Commutator> commutatorWithCommunity = new ArrayList<>();

    private final int CHECK_TIME = 30;// в минутах;

    @Value("${rwcommunity}")
    private String COMMUNITY;


    @PostConstruct
    public void init(){
        System.out.println("Удаляем прописанные коммьнити");
        commutatorWithCommunity = commutatorService.getAllCommutators();
    }


    @Scheduled(fixedDelay = CHECK_TIME * 60000,initialDelay =  CHECK_TIME * 60000)
    public void deleteCommunities(){
        System.out.println(Functions.getTime()+" Удаление коммьюнити запущено");
        for(Commutator commutator : commutatorWithCommunity){
            commutator.setTelnetParams(telnetLogin,telnetPassword);
            commutator.setStrategy(strategyMap.get(commutator.model().getOID()));
            try{
                commutator.deleteCommunity("engforta-rw");
            }catch(Exception e){
                //
            }
            commutatorWithCommunity.remove(commutator);
        }
        System.out.println(Functions.getTime()+" Удаление коммьюнити закончено");
    }
}

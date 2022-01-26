package com.uospd.switches;

import com.uospd.services.LoggingService;
import com.uospd.switches.exceptions.ConnectException;
import com.uospd.switches.exceptions.InvalidDatabaseOIDException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.exceptions.UnsupportedCommutatorException;
import com.uospd.switches.interfaces.CommutatorStrategy;
import com.uospd.utils.Network;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CommutatorConnector{
    private final LoggingService logger;

    @Value("${telnet.login}")
    private String telnetLogin;
    @Value("${telnet.password}")
    private String telnetPassword;

    private final Map<String, Commutator> connectionPool = new TreeMap<>();
    private final Map<String, Integer> connectedToSwitch = new HashMap<>();
    private final Queue<Commutator> commutatorQueue = new LinkedList<>();

    @Autowired @Qualifier("strategyMapBean")
    private final Map<String,CommutatorStrategy> strategyMap;

    @PostConstruct
    private void init(){
        addStrategy("1.3.6.1.4.1.8886.6.140",null);
    }

    public Commutator getFeaturedCommutator(Commutator commutator, String snmpCommunity) throws ConnectException, UnsupportedCommutatorException, InvalidDatabaseOIDException{
        String ip = commutator.getIp();
        boolean ping = Network.ping(ip, 300);
        if(!ping) throw new ConnectException("No Ping to commutator");
        if(connectionPool.containsKey(ip)){
            logger.debug(ip + " уже есть в пуле, отдаем его пользователю");
            connectedToSwitch.put(ip, connectedToSwitch.get(ip) + 1);
            return connectionPool.get(ip);
        }
        commutator.enableSnmp(snmpCommunity);
        try{
            String objectoid = commutator.getResponse(".1.3.6.1.2.1.1.2.0");
            if(objectoid.equals("Null")) throw new ConnectException("No Object ID for " + ip);
            else if(!objectoid.equals(commutator.modelInfo().getOID())){
                throw new InvalidDatabaseOIDException("Wrong OID in database for:"+ip);
            }
            if(strategyMap.containsKey(objectoid)) commutator.setStrategy(strategyMap.get(objectoid));
            else throw new UnsupportedCommutatorException("Strategy for commutator not found");
        }catch(NoSnmpAnswerException | NullPointerException e){
            throw new ConnectException("No Object ID for " + ip, e);
        }
        commutator.setTelnetParams(telnetLogin,telnetPassword);
        connectionPool.put(ip, commutator);
        connectedToSwitch.put(ip, connectedToSwitch.getOrDefault(ip,0)+1);
        return commutator;
    }

    public void addStrategy(String oid,CommutatorStrategy strategy){
        strategyMap.put(oid,strategy);
    }

    public String getSwitchPoolInfo(){
        StringBuilder a = new StringBuilder("POOL:\n");
        connectedToSwitch.forEach((k, v) -> a.append(k).append(":").append(v).append("\n"));
        return a.toString();
    }

    private boolean existInPool(String ip){
        return connectionPool.containsKey(ip);
    }

    public void disconnect(Commutator commutator){
        String ip = commutator.getIp();
        if(!existInPool(ip)) return;
        if(connectionPool.containsKey(ip) && connectedToSwitch.containsKey(ip)){
            int connected = connectedToSwitch.get(ip) - 1;
            if(connected < 1){
                connectedToSwitch.remove(ip);
                connectionPool.remove(ip);
                commutator.disconnect();
                return;
            }
            connectedToSwitch.put(ip, connected);
        }
    }
}
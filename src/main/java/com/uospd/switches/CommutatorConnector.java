package com.uospd.switches;

import com.uospd.services.LoggingService;
import com.uospd.switches.exceptions.ConnectException;
import com.uospd.switches.exceptions.InvalidDatabaseOIDException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.exceptions.UnsupportedCommutatorException;
import com.uospd.switches.interfaces.CommunityCreateStrategy;
import com.uospd.switches.interfaces.CommutatorStrategy;
import com.uospd.utils.Network;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CommutatorConnector{
    @Autowired
    private final LoggingService logger;

    @Value("${telnet.login}")
    private String telnetLogin;
    @Value("${telnet.password}")
    private String telnetPassword;

    private final Map<String, Commutator> connectionPool = new TreeMap<>();
    private final Map<String, Integer> connectedToSwitch = new HashMap<>();

    //private List<String> communityAdded = new ArrayList<>();

    @Autowired @Qualifier("strategyMapBean")
    private final Map<String,CommutatorStrategy> strategyMap;

    @PostConstruct
    private void init(){
        addStrategy("1.3.6.1.4.1.8886.6.140",null);
    }

    public Commutator getFeaturedCommutator(Commutator commutator, String snmpCommunity) throws ConnectException, UnsupportedCommutatorException, InvalidDatabaseOIDException, NoSnmpAnswerException{
        String ip = commutator.getIp();
        logger.connectionsLog(ip+": connecting");
        if(!Network.ping(ip, 300)){
           // ??? if(existInPool(ip)) disconnect(connectionPool.get(ip));
            logger.connectionsLog(ip+": нет пинга");
            throw new ConnectException("No Ping to commutator");
        }
        if(connectionPool.containsKey(ip)){
            logger.connectionsLog(ip + " уже есть в пуле, отдаем его пользователю");
            connectedToSwitch.put(ip, connectedToSwitch.get(ip) + 1);
            return connectionPool.get(ip);
        }

        String bdOid = commutator.model().getOID();
        if(!strategyMap.containsKey(bdOid)){
            logger.connectionsLog(ip+": Strategy for "+bdOid+" not found");
            throw new UnsupportedCommutatorException("Strategy for commutator not found");
        }
        commutator.setStrategy(strategyMap.get(bdOid));
        commutator.setTelnetParams(telnetLogin,telnetPassword);

        //noinspection ConstantConditions
        for(int i = 1;i<=2;i++){ // две попытки
            logger.connectionsLog(ip+": попытка подключения №"+i);
            try{
                commutator.enableSnmp(snmpCommunity);
                String objectoid = commutator.getResponse(".1.3.6.1.2.1.1.2.0");
                if(objectoid.equals("Null")){
                    commutator.disconnectSnmp();
                    logger.connectionsLog(ip+": OBJECTOID IS NULL");
                    throw new ConnectException("No Object ID for " + ip);
                }else if(!objectoid.equals(bdOid)){ // если  objectoid не соответствует хранящемуся в БД
                    commutator.disconnectSnmp();
                    logger.connectionsLog(ip+": Wrong OID in database");
                    throw new InvalidDatabaseOIDException("Wrong OID in database for:" + ip);
                }
            }catch(NoSnmpAnswerException e){
                commutator.disconnectSnmp();
                logger.connectionsLog(ip+": NoSnmpAnswerException");
                throw new NoSnmpAnswerException("No Object ID for " + ip);
            }catch(NullPointerException e){ // Если не прописано коммьюнити
                commutator.disconnectSnmp();
                if(i == 1){
                    logger.connectionsLog(ip+": произошла ошибка при первом подключении");
                    if(commutator.hasStrategy(CommunityCreateStrategy.class)){
                        try{
                            commutator.createCommunity(snmpCommunity);
                        }catch(Exception ex){
                            e.printStackTrace();
                        }
                        logger.connectionsLog(ip+": creating community");
                        //communityAdded.add(ip);
                        continue;
                    }
                    else{
                        logger.connectionsLog(ip+": прописка коммьюнити недоступна");
                    }
                }
                logger.connectionsLog(ip+": NullPointerException");
                throw new ConnectException("NullPointerException " + ip, e);
            }
            connectionPool.put(ip, commutator);
            connectedToSwitch.put(ip, connectedToSwitch.getOrDefault(ip, 0) + 1);
            logger.connectionsLog(ip+": connected");
            return commutator;
        }
        logger.connectionsLog(ip+": ConnectException");
         throw new ConnectException("Не удалось подключиться");
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
                commutator.disconnectSnmp();
                return;
            }
            connectedToSwitch.put(ip, connected);
        }
    }
}
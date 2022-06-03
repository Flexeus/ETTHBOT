package com.uospd.switches;

import com.uospd.switches.exceptions.ConnectException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.interfaces.*;
import com.uospd.utils.Functions;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Table(name = "realip",catalog = "monitoring")
@Entity
@NoArgsConstructor
public class Commutator{
    private static final String PORT_DESCRIPTION_OID = "1.3.6.1.2.1.31.1.1.1.18.";
    private static final String PORT_STATE_OID = ".1.3.6.1.2.1.2.2.1.7.";
    private static final String PORT_SPEED_OID = ".1.3.6.1.2.1.31.1.1.1.15.";
    private static final String PORT_LINK_OID = ".1.3.6.1.2.1.2.2.1.8.";
    private static final String MACS_OID = "1.3.6.1.2.1.17.7.1.2.2.1.2.";
    private static final String PORT_ERRORS_OID = ".1.3.6.1.2.1.2.2.1.14.";
    private static final String COMMUTATOR_HOSTNAME_OID = ".1.3.6.1.2.1.1.5.0";

    @Id
    @Column(name = "ipaddr")
    @Getter
    private String ip;

    @ManyToOne
    @JoinColumn(name = "ObjectID",nullable = false)
    private CommutatorModel modelInfo;

    @Column(name = "PppoeVlan")
    private Integer pppoeVlan;

    @Column
    private Integer vertical;

    @Getter @Column
    private String street;

    @Getter @Column
    private String home;

    @Column
    private Integer porch;

    private transient Telnet telnet;
    private transient String telnetLogin;
    private transient String telnetPassword;
    private transient SnmpManager snmpManager;
    private transient String createdCommunity;

    private transient List<Class<? extends CommutatorStrategy>> strategyList = new ArrayList<>();
    private transient CableTestStrategy cableTestStrategy;
    private transient DDMStrategy ddmStrategy;
    private transient DropCountersStrategy dropCountersStrategy;
    private transient VlanShowingStrategy vlanShowingStrategy;
    private transient CommunityCreateStrategy communityCreateStrategy;

    public Commutator(String ip) {
        this.ip = ip;
    }

    public boolean hasStrategy(Class<? extends CommutatorStrategy> strategyClass){
        for(Class<? extends CommutatorStrategy> cl : strategyList){
            Class<?>[] interfaces = cl.getInterfaces();
            for(Class<?> interf: interfaces) if(interf == strategyClass) return true;
        }


        if(cableTestStrategy!= null && cableTestStrategy.getClass().isInstance(strategyClass)) return true;
        if(ddmStrategy!= null){
            Class<?>[] interfaces = ddmStrategy.getClass().getInterfaces();
            for(Class<?> cl: interfaces) if(cl == strategyClass) return true;
        }
        if(dropCountersStrategy!= null){
            Class<?>[] interfaces = dropCountersStrategy.getClass().getInterfaces();
            for(Class<?> cl: interfaces) if(cl == strategyClass) return true;
        }
        if(vlanShowingStrategy!= null){
            Class<?>[] interfaces = vlanShowingStrategy.getClass().getInterfaces();
            for(Class<?> cl: interfaces) if(cl == strategyClass) return true;
        }
        if(communityCreateStrategy != null){
            Class<?>[] interfaces = communityCreateStrategy.getClass().getInterfaces();
            for(Class<?> cl: interfaces) if(cl == strategyClass) return true;
        }
        return false;
    }

    public void setStrategy(CommutatorStrategy commutatorStrategy){
        if(commutatorStrategy instanceof CableTestStrategy) cableTestStrategy = (CableTestStrategy) commutatorStrategy;
        if(commutatorStrategy instanceof DropCountersStrategy) dropCountersStrategy = (DropCountersStrategy) commutatorStrategy;
        if(commutatorStrategy instanceof VlanShowingStrategy) vlanShowingStrategy = (VlanShowingStrategy) commutatorStrategy;
        if(commutatorStrategy instanceof DDMStrategy) ddmStrategy = (DDMStrategy) commutatorStrategy;
        if(commutatorStrategy instanceof CommunityCreateStrategy) communityCreateStrategy = (CommunityCreateStrategy) commutatorStrategy;
    }

    public void setTelnetParams(String login,String password){
        this.telnetLogin = login;
        this.telnetPassword = password;
    }

    public void enableSnmp(String community) throws ConnectException {
        if(ip == null) throw new ConnectException("IP is null");
        try {
            snmpManager = new SnmpManager(ip, community);
        } catch (IOException e){
            this.disconnectSnmp();
            throw new ConnectException("Failed to create snmp connection",e);
        }
    }

    public boolean isAUpLink(int port){
        return port > modelInfo.getPortsCount() && port <= modelInfo.getPortsCount() + modelInfo.getUpLinkCount();
    }

    public boolean isTrunkPort(int port){
        return modelInfo.isAgregation() || isAUpLink(port);
    }

    public String getPortInfo(int port){
        try{
            int portState = getPortState(port);
            StringBuilder portInfo = new StringBuilder("Порт: " + port);
            portInfo.append("\nОписание: " + getPortDescription(port));
            portInfo.append("\nСостояние: " + (portState == 2 ? "Закрыт" : "Открыт"));
            if(portState != 2)
                portInfo.append("\nЛинк: " + (portLinkStatus(port) ? "есть(" + getPortSpeed(port) + " мбит)" : "нет"));
            portInfo.append("\nОшибок: " + getErrorsCount(port));
            if(!isAUpLink(port) && !modelInfo.isAgregation()) portInfo.append("\n" + cableTest(port));
            return portInfo.toString();
        }catch(NoSnmpAnswerException e){
            return "Не удалось получить состояние порта";
        }
    }

    public String getDDMInfo(int port){
        if(ddmStrategy == null) return "DDM не доступен на данном коммутаторе";
        return ddmStrategy.getDDMInfo(snmpPort(port),this);
    }

    public String cableTest(int port){
        if(cableTestStrategy == null) return "Кабель-тест не поддерживается на данном коммутаторе";
        return cableTestStrategy.snmpCableTest(port,this);
    }

    public void dropCounters(int port) throws Exception{
        if(dropCountersStrategy == null) return;
        dropCountersStrategy.dropCounters(this, port);
    }

    public String getHostName(){
        return getResponse(COMMUTATOR_HOSTNAME_OID,"unknown");
    }

    public String getPortDescription(int port){
        return getResponse(PORT_DESCRIPTION_OID+snmpPort(port),"");
    }

    public String showVlans(int port){
        return vlanShowingStrategy.showVlans(port,this);
    }

    public int getPortState(int port) throws NoSnmpAnswerException{ // 1 - up, 2 - down.
            return Integer.parseInt(getResponse(PORT_STATE_OID + snmpPort(port)));
    }

    public int getErrorsCount(int port){
        return Integer.parseInt(getResponse(PORT_ERRORS_OID + snmpPort(port),"0"));
    }

    public int getErrorsCountWithError(int port) throws NoSnmpAnswerException{
        return Integer.parseInt(getResponse(PORT_ERRORS_OID + snmpPort(port)));
    }

    public String getPortSpeed(int port){
        return getResponse(PORT_SPEED_OID + snmpPort(port),"неизвестно");
    }

    public boolean portLinkStatus(int port){
        String link = getResponse(PORT_LINK_OID+snmpPort(port),"0");
        return link.equals("1");
    }

    public String getPortsStatus(){
        StringBuilder finalstr = new StringBuilder("Порт    Линк    Описание\n");
        for(int i = 1;i <= model().getPortsCount() + model().getUpLinkCount();i++){
            String tempi = String.valueOf(i < 10 ? "0" + i : i);
            String link = (portLinkStatus(i) ? "UP" : "      ");
            finalstr.append(tempi).append("         ").append(link).append("         ").append(getPortDescription(i)).append("\n");
        }
        return finalstr.toString();
    }

    public int[] getAllLinks(){
        int[] all = new int[modelInfo.getPortsCount()];
        for(int i = 0;i < modelInfo.getPortsCount();i++) all[i] = portLinkStatus(i) ? 1 : 0;
        return all;
    }

    public CommutatorModel model() {
        return modelInfo;
    }

    public String getVertical(){
        return switch(vertical){
            case 0 -> "";
            case 99 -> "чердак";
            case 98 -> "подвал";
            default -> vertical + " этаж";
        };
    }

    public int getPorch() {
        if (porch == null) return 0;
        return porch;
    }

    public boolean restartPort(int port) {
        int snmpPort = snmpPort(port);
        snmpSet(PORT_STATE_OID + snmpPort, 2); // закрываем порт
        if (Integer.parseInt(getResponse(PORT_STATE_OID + snmpPort,"0")) == 2) {
            snmpSet(PORT_STATE_OID + snmpPort, 1); // открываем
            return Integer.parseInt(getResponse(PORT_STATE_OID + snmpPort,"0")) == 1;
        }
        return false;
    }

    public String getMacsOnPort(int port){
        Map<String, String> map = snmpManager.walkExtract(MACS_OID);
        StringBuilder result = new StringBuilder();
        int macCounter=0;
        for(Map.Entry<String, String> entry : map.entrySet()){
            if(Integer.parseInt(entry.getValue()) != snmpPort(port)) continue; // отсеиваем маки с других портов
            String k = entry.getKey();
            macCounter++;
            String[] octets = k.substring(k.indexOf(".") + 1).split("\\."); // mac
            result.append("[").append(k, 0, k.indexOf(".")).append("] "); // vlan
            for(int i = 0;i < octets.length;i++){
                result.append(Functions.decToHex(Integer.parseInt(octets[i]))).append(i < octets.length - 1 ? ":" : "");
            }
            result.append("\n");
        }
        if(macCounter == 0) return "Маков нет :(";
        result.append("Всего маков:").append(macCounter);
        return result.toString();
    }

    public Integer getPppoeVlan() {
        return pppoeVlan;
    }

    public boolean supportingDDM(){
        return ddmStrategy != null;
    }
    public boolean supportingDropCounters(){
        return dropCountersStrategy != null;
    }
    public boolean supportingShowVlans(){
        return vlanShowingStrategy != null;
    }

    public int snmpPort(int port){
        if(isAUpLink(port)) return port-modelInfo.getPortsCount()-1+modelInfo.getFirstUpLinkID();
        return modelInfo.getFirstPortID()-1+port;
    }

    public String executeAndRead(String...cmd) throws Exception{
        telnet = new Telnet(telnetLogin,telnetPassword,this);
        telnet.send(cmd);
        //  telnet.close();
        return telnet.returnCMDResult();
    }

    public void executeTelnetCommands(String...cmd) throws Exception{
        telnet = new Telnet(telnetLogin,telnetPassword,this);
        telnet.connect();
        telnet.send(cmd);
//        String s = telnet.returnCMDResult();
//        System.out.println(s);
        telnet.close();
    }


    public String getResponse(String response) throws NoSnmpAnswerException{
        if(snmpManager == null) throw new NullPointerException("SnmpManager are null");
        return snmpManager.getResponse(response);
    }

    public String getResponse(String response, String onException){
        try{
            return snmpManager.getResponse(response);
        }catch(NoSnmpAnswerException | NullPointerException e ){
            System.out.println(e.getMessage());
            return onException;
        }
    }


    public void snmpSet(String response, int value){
        if(snmpManager == null) throw new NullPointerException("SnmpManager are null");
        snmpManager.snmpSet(response,value);
    }

    public void snmpSet(String response, String value){
        if(snmpManager == null) throw new NullPointerException("SnmpManager are null");
        snmpManager.snmpSetString(response,value);
    }

    public final boolean ping(int mills){
        try {
            Socket sock =  new Socket();
            sock.connect(new InetSocketAddress(ip, 23), mills);
            sock.close();
            return true;
        }
        catch (IOException e){ return false; }
    }

    public final void disconnectSnmp(){

        try{
            if(createdCommunity!=null) deleteCommunity(createdCommunity);
            if(snmpManager == null) return;
            snmpManager.stop();
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            snmpManager = null;
        }
    }

    @Override
    public String toString() {
        return "Commutator{" +
                "ip='" + getIp() +
                ", snmp=" + snmpManager +
                ", PPPoEVlan=" + pppoeVlan +
                ", street=" + street +
                ", home=" + home +
                ", modelInfo=" + modelInfo.getModel() +
                '}';
    }

    public void createCommunity(String community) throws Exception{
        communityCreateStrategy.writeCommunity(community,this);
        createdCommunity = community;
    }
    public void deleteCommunity(String community) throws Exception{
        communityCreateStrategy.deleteCommunity(community,this);
    }

    public Telnet telnet(){
        return telnet;
    }
}

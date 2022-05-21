package com.uospd.switches.strategies;


import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.interfaces.*;

import java.util.ArrayList;
import java.util.List;

@CommutatorStrategyComponent({"1.3.6.1.4.1.35265.1.52", //MES1124M
        "1.3.6.1.4.1.35265.1.30", // MES3124
        "1.3.6.1.4.1.35265.1.81", // MES3324
        "1.3.6.1.4.1.35265.1.42", // MES1024
        "1.3.6.1.4.1.35265.1.39", // MES3116F
        "1.3.6.1.4.1.35265.1.74" // MES5324
})
public class MES1124 implements CableTestStrategy, DropCountersStrategy, VlanShowingStrategy, DDMStrategy, CommunityCreateStrategy{

    public void writeCommunity(String community, Commutator commutator) throws Exception{
        commutator.executeTelnetCommands("config","snmp-server community "+community+" rw","exit");
    }

    public void deleteCommunity(String community, Commutator commutator) throws Exception{
        commutator.executeTelnetCommands("config","no snmp-server community "+community,"exit");
    }

    public String getDDMInfo(int port, Commutator commutator){
        String rxPower = null;
        String txPower = null;
        String temperature = commutator.getResponse(".1.3.6.1.4.1.89.90.1.2.1.3."+port+".5","-");
        try{ Thread.sleep(500); }catch(InterruptedException e){ e.printStackTrace(); } // было 1000
        String rxAnswer = commutator.getResponse(".1.3.6.1.4.1.89.90.1.2.1.3."+port+".9",null);
        try{ Thread.sleep( 1000); }catch(InterruptedException e){ e.printStackTrace(); } // было 1500
        if(rxAnswer != null)  rxPower = String.valueOf(Double.parseDouble(rxAnswer)/1000);
        String txAnswer = commutator.getResponse(".1.3.6.1.4.1.89.90.1.2.1.3."+port+".8",null);
       // try{ Thread.sleep(1000);  }catch(InterruptedException e){ e.printStackTrace(); }
        if(txAnswer != null)  txPower = String.valueOf(Double.parseDouble(txAnswer)/1000);
        return ddmTemplate(temperature,rxPower,txPower);
    }

    private int getPortConnectionType(int port,Commutator commutator){
        try{
            return Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.89.43.1.1.7."+port)); // regular(1), fiberOptics(2), comboRegular(3), comboFiberOptics(4)
        }catch(NoSnmpAnswerException e){
            e.printStackTrace();
            return 0;
        }
    }

    public String snmpCableTest(int port,Commutator commutator) throws NullPointerException {
        port = commutator.snmpPort(port);
        if(commutator.portLinkStatus(port)) return "Кабель-тест невозможен при поднятом линке";
        commutator.snmpSet(".1.3.6.1.4.1.89.90.1.1.1.1." + port, 2);
        try{
            Thread.sleep(300);
            int fpstate = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.35265.1.23.90.1.1.1.2." + port));
            int spstate = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.35265.1.23.90.1.1.1.3." + port));
            String fplength = commutator.getResponse(".1.3.6.1.4.1.35265.1.23.90.1.1.1.6." + port);
            String splength = commutator.getResponse(".1.3.6.1.4.1.35265.1.23.90.1.1.1.7." + port);
            return strCabletest(fpstate, spstate, fplength, splength);
        }
        catch(Exception e){
            return "Произошла ошибка при выполнении кабель-теста";
        }
    }

    public String strCabletest(int fpairstate,int spairstate,String fpairlength,String spairlength){
        String[] states = {"   Ошибка теста", "   OK               ", "   обрыв        ", "   КЗ               ", "   затухание", "   кз межпарное", "   кз между парами", "   кз между парами"};
        return  "Кабель тест:\n-----------------------------------------------\n" +
                "Пара         статус          длина(м)\n" +
                "----------       --------------      --------------\n" +
                "1              "+states[fpairstate]+"        "+fpairlength+"\n" +
                "2              "+states[spairstate]+"        "+spairlength;
    }

    public List<Integer> getVlansOnPort(int snmpPort,Commutator commutator){
        List<Integer> vlans = new ArrayList<>();
        for(int i=1;i<5;i++) {
            String result = commutator.getResponse("1.3.6.1.4.1.89.48.68.1."+i+"."+snmpPort,"0");
            vlans.addAll(DecPorts(result,i));
        }
        return vlans;
    }

    public List<Integer> getUntaggedVlansOnPort(int snmpPort,Commutator commutator){
        List<Integer> vlans = new ArrayList<>();
        for(int i=5;i<9;i++) {
            String result = commutator.getResponse("1.3.6.1.4.1.89.48.68.1." + i+"."+snmpPort,"0");
            vlans.addAll(DecPorts(result,i-4));
        }
        return vlans;
    }
    public List<Integer> getTaggedVlansOnPort(int snmpPort, Commutator commutator, List<Integer> untaggedVlans){
        List<Integer> allVlansOnPort = getVlansOnPort(snmpPort,commutator);
        List<Integer> taggedVlansOnPort = new ArrayList<>(allVlansOnPort);
        taggedVlansOnPort.removeAll(untaggedVlans);
        return taggedVlansOnPort;
    }

    public String showVlans(int port,Commutator commutator){
        int snmpPort = commutator.snmpPort(port);
        String vlans= "";
        List<Integer> untaggedVlansOnPort = getUntaggedVlansOnPort(snmpPort,commutator);
        List<Integer> taggedVlansOnPort = getTaggedVlansOnPort(snmpPort,commutator,untaggedVlansOnPort);
        if(!untaggedVlansOnPort.isEmpty()) vlans+="Untag: "+ untaggedVlansOnPort.toString()+" ";
        if(!taggedVlansOnPort.isEmpty()) vlans+= "Tag: "+ taggedVlansOnPort.toString();
        vlans = vlans.replace("1299","Unicast").replace("1340","VOIP").replace("4040","Активатор");
        return vlans;
    }

    public String getPortInterface(int port,Commutator commutator) {
        return commutator.getResponse(".1.3.6.1.2.1.2.2.1.2."+'.'+port,"0");
    }

    public void dropCounters(Commutator commutator,int port) throws Exception{
        commutator.executeTelnetCommands("clear counters " + getPortInterface(commutator.snmpPort(port), commutator),"exit");
    }

}

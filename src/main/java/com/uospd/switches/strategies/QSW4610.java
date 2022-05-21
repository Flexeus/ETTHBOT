package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.interfaces.*;
import lombok.SneakyThrows;

import java.util.Collections;
import java.util.List;

@CommutatorStrategyComponent({"1.3.6.1.4.1.27514.1.1.1.344", "1.3.6.1.4.1.27514.1.1.1.410",
                              "1.3.6.1.4.1.27514.1.1.1.310", "1.3.6.1.4.1.27514.1.1.1.355"})
public class QSW4610  implements CableTestStrategy, DropCountersStrategy, VlanShowingStrategy, DDMStrategy, CommunityCreateStrategy{
    private Commutator commutator;

    @Override public void writeCommunity(String community, Commutator commutator) throws Exception{
        commutator.executeTelnetCommands("config","snmp-server community rw 0 "+community,"q","q");
    }

    @Override public void deleteCommunity(String community, Commutator commutator) throws Exception{
        commutator.executeTelnetCommands("config","no snmp-server community 0 "+community,"q","q");
    }

    public String snmpCableTest(int port, Commutator commutator){
        commutator.snmpSet("1.3.6.1.4.1.27514.100.3.2.1.18."+port,1);
        try{
            Thread.sleep(400);
            String output = commutator.getResponse("1.3.6.1.4.1.27514.100.3.2.1.19." + port);
            //Обработка вывода
            output = output.replace("Interface Ethernet1/0/" + port + "", "Кабель тест")
                    .replace("Cable pairs", "Пара    ").replace("Cable status", "статус")
                    .replace("Length (meters)", " длина(м)")
                    .replace("open", "обрыв")
                    .replace("short", "КЗ   ")
                    .replace("well", "  ОК   ");
            return output;
        }catch(Exception e){
            return "Произошла ошибка при выполнении кабель-теста";
        }
    }

    private int getPortMode(int port) throws NoSnmpAnswerException{  //1 - access, 2 - trunk, 3 - hybrid
        return Integer.parseInt(commutator.getResponse(".1.3.6.1.4.1.27514.100.3.2.1.15." + port));
    }

    @SneakyThrows
    public String getDDMInfo(int port,Commutator commutator){
        String temperature = commutator.getResponse(".1.3.6.1.4.1.27514.100.30.1.1.2." + port,"-");
        Thread.sleep(3000); // не меньше 3 сек
        String rxPower = commutator.getResponse(".1.3.6.1.4.1.27514.100.30.1.1.17." + port,"-");
        Thread.sleep(5000);  // Меньше 5секунд - крашится
        String txPower = commutator.getResponse(".1.3.6.1.4.1.27514.100.30.1.1.22." + port,"-");
        Thread.sleep(1000);
        return ddmTemplate(temperature,rxPower,txPower);
    }

//    private boolean addTaggedVlan(int port, int vlan) {
//        List<Integer> taggedVlans = getTaggedVlans(port);
//        StringBuilder tvlans = new StringBuilder();
//        for (int a : taggedVlans) tvlans.append(a).append(",");
//        tvlans.append(vlan);
//        commutator.snmpSet(".1.3.6.1.4.1.27514.100.3.2.1.21." + port, tvlans.toString());
//        return getTaggedVlans(port).contains(vlan);
//    }

    public String showVlans(int port,Commutator commutator){
        this.commutator = commutator;
        StringBuilder vlans = new StringBuilder();
        List<Integer> UntaggedVlansOnPort = getUntaggedVlansOnPort(port);
        List<Integer> TaggedVlansOnPort = getTaggedVlans(port);
        if(!TaggedVlansOnPort.isEmpty()) vlans.append("Tag: ").append(TaggedVlansOnPort.toString());
        if(!UntaggedVlansOnPort.isEmpty()) vlans.append("\nUntag: ").append(UntaggedVlansOnPort.toString()).append(" ");
        try{
            if(getPortMode(port) == 3) vlans.append("\nNative:"+getNativeVlan(port)); // для Hybrid выведем еще и nativeVlan
        }catch(NoSnmpAnswerException e){
            e.printStackTrace();
        }
        if(UntaggedVlansOnPort.isEmpty() && TaggedVlansOnPort.isEmpty()) return "На порту не установлены вланы";
        return vlans.toString().replace("1299","Unicast").replace("1340","VOIP").replace("4040","Активатор").replace(String.valueOf(commutator.getPppoeVlan()),"PPPoE");
    }

    private List<Integer> getUntaggedVlansOnPort(int port){
        try{
            int portMode = getPortMode(port);
            if(portMode == 3) return DecPorts(commutator.getResponse(".1.3.6.1.4.1.27514.100.3.2.1.22." + port), 0); // для режима Hybrid
            else return List.of(getNativeVlan(port));   // Для access и trunk одинаковый oid и только 1 влан
        }catch(NoSnmpAnswerException e){
            return Collections.emptyList();
        }
    }

    private List<Integer> getTaggedVlans(int port){
        try{
            int portMode = getPortMode(port);
            if(portMode == 3) return DecPorts(commutator.getResponse(".1.3.6.1.4.1.27514.100.3.2.1.21." + port), 0); // hybrid
            else if(portMode == 2) return DecPorts(commutator.getResponse(".1.3.6.1.4.1.27514.100.3.2.1.20." + port), 0); // trunk
            else return Collections.emptyList(); // у режима access не может быть тегированного влана
        }catch(NoSnmpAnswerException e){
            return Collections.emptyList();
        }
    }

    private int getNativeVlan(int port){
        String response = commutator.getResponse(".1.3.6.1.2.1.17.7.1.4.5.1.1." + port, "0");
        return Integer.parseInt(response);
    }

    private boolean setNativeVlan(int port, int pppoevl) {
        commutator.snmpSet(".1.3.6.1.4.1.27514.100.3.2.1.16."+port,pppoevl);
        try { Thread.sleep(400); } catch (InterruptedException e) { e.printStackTrace(); }
        if(getNativeVlan(port) == pppoevl) return true;
        return false;
    }


    public void dropCounters(Commutator commutator,int port) throws Exception{
        commutator.executeTelnetCommands("clear counters interface ethernet 1/0/"+port,"exit");
    }

}

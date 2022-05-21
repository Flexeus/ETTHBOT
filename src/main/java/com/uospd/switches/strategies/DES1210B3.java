package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.interfaces.DDMStrategy;
import com.uospd.switches.interfaces.DLinkCableTestStrategy;
import com.uospd.switches.interfaces.DropCountersStrategy;

@CommutatorStrategyComponent("1.3.6.1.4.1.171.10.75.15.3")
public class DES1210B3 implements DDMStrategy, DLinkCableTestStrategy, DropCountersStrategy{
    @Override
    public String snmpCableTest(int port, Commutator commutator){
        try{
            commutator.snmpSet(".1.3.6.1.4.1.171.10.75.15.3.35.1.1.12." + port, 1);
            int status = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.171.10.75.15.3.35.1.1.13." + port));
            if(status != 3) Thread.sleep(500);
            if(status == 4) return "Не удалось совершить кабель тест";
            String firstpairstate = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.3.35.1.1.5." + port); // состояние первой пары
            String secondpairstate = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.3.35.1.1.6." + port);
            String pair1length = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.3.35.1.1.9." + port); // состояние длина первой пары
            String pair2length = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.3.35.1.1.10." + port); // состояние длины второй пары
            return strCabletest(firstpairstate, secondpairstate, pair1length, pair2length);
        }catch(InterruptedException | NoSnmpAnswerException e){
            return "Не удалось совершить кабель тест";
        }
    }

    public String getDDMInfo(int port, Commutator commutator){
        String rxPower = commutator.getResponse(".1.3.6.1.4.1.171.10.75.15.3.105.2.1.1.1.6." + port,"-");
        String txPower = commutator.getResponse(".1.3.6.1.4.1.171.10.75.15.3.105.2.1.1.1.5." + port,"-");
        String temperature = commutator.getResponse(".1.3.6.1.4.1.171.10.75.15.3.105.2.1.1.1.2." + port,"-");
        return ddmTemplate(temperature, rxPower, txPower);
    }

    @Override
    public void dropCounters(Commutator commutator,int port) throws Exception{
        commutator.executeTelnetCommands("clear counters ports "+port);
    }
}
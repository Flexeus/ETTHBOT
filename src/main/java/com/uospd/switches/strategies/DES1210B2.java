package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.interfaces.DDMStrategy;

@CommutatorStrategyComponent("1.3.6.1.4.1.171.10.75.15.2")
public class DES1210B2 extends DES3526 implements DDMStrategy{
    @Override
    public String snmpCableTest(int port, Commutator commutator){
        commutator.snmpSet(".1.3.6.1.4.1.171.10.75.15.2.35.1.1.12." + port, 1);
        try{
            int status = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.171.10.75.15.2.35.1.1.13." + port));
            if(status != 3) Thread.sleep(500);
            if(status == 4) return "Не удалось совершить кабель тест";
            String firstpairstate = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.2.35.1.1.5." + port); // состояние первой пары
            String secondpairstate = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.2.35.1.1.6." + port);
            String pair1length = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.2.35.1.1.9." + port); // состояние длина первой пары
            String pair2length = commutator.getResponse("1.3.6.1.4.1.171.10.75.15.2.35.1.1.10." + port); // состояние длины 2 par\
            return strCabletest(firstpairstate, secondpairstate, pair1length, pair2length);
        }catch(InterruptedException | NoSnmpAnswerException e){
            return "Не удалось совершить кабель тест";
        }
    }

    public String getDDMInfo(int port, Commutator commutator){
        String rxPower = commutator.getResponse(".1.3.6.1.4.1.171.10.75.15.2.105.2.1.1.1.2." + port, "-");
        String txPower = commutator.getResponse(".1.3.6.1.4.1.171.10.75.15.2.105.2.1.1.1.6." + port,"-");
        String temperature = commutator.getResponse(".1.3.6.1.4.1.171.10.75.15.2.105.2.1.1.1.5." + port,"-");
        return ddmTemplate(temperature, rxPower, txPower);
    }
}

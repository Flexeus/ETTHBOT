package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;

@CommutatorStrategyComponent("1.3.6.1.4.1.171.10.113.1.3")
public class DES3200_28 extends DES3526{

    public String snmpCableTest(int port, Commutator commutator){
        commutator.snmpSet("1.3.6.1.4.1.171.12.58.1.1.1.12."+port,1);
        try {
            Thread.sleep(500);
            int status = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.12." + port));
            if(status == 2) return "Не удалось совершить кабель тест";
            String firstpairstate = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.4." + port); // состояние pair1
            String secondpairstate = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.5." + port); // состояние pair2
            String pair1length = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.8." + port); // длина pair1
            String pair2length = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.9." + port); // длины pair2
            return strCabletest(firstpairstate, secondpairstate, pair1length, pair2length);
        }catch(InterruptedException | NoSnmpAnswerException e){
            return "Не удалось совершить кабель тест";
        }
    }
}

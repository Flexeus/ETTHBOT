package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;

@CommutatorStrategyComponent({"1.3.6.1.4.1.171.10.113.1.5", "1.3.6.1.4.1.171.10.113.4.1", "1.3.6.1.4.1.171.10.113.5.1"})
public class DES3200 extends DES3526{
    @Override
    public String snmpCableTest(int port, Commutator commutator){
        commutator.snmpSet("1.3.6.1.4.1.171.12.58.1.1.1.12."+port,1);
        try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); }
        try{
            int status = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.12." + port));
            if(status == 2)  return "Не удалось совершить кабель тест";
            String pair1length = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.9." + port); // длина pair2
            String pair2length = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.10." + port); // длины pair3
            String firstpairstate = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.5." + port); // состояние pair2
            String secondpairstate = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.6." + port); // состояние pair3
            if(firstpairstate.equals("7") || secondpairstate.equals("7")){ // если кабеля нет, то метраж = 0
                pair1length = "0";
                pair2length = "0";
            }
            return strCabletest(firstpairstate,secondpairstate,pair1length,pair2length);
        }
        catch(NoSnmpAnswerException e){
            return "Не удалось совершить кабель тест";
        }
    }
}

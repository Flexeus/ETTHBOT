package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.interfaces.DLinkCableTestStrategy;
import com.uospd.switches.interfaces.DropCountersStrategy;


@CommutatorStrategyComponent({"1.3.6.1.4.1.171.10.113.1.1", "1.3.6.1.4.1.171.10.113.2.1",
                              "1.3.6.1.4.1.171.10.113.1.2", "1.3.6.1.4.1.171.10.64.1"})
public class DES3526  implements DLinkCableTestStrategy, DropCountersStrategy {

    public String snmpCableTest(int port, Commutator commutator){
        commutator.snmpSet("1.3.6.1.4.1.171.12.58.1.1.1.12."+port,1);
        try{
            Thread.sleep(500);
            int status = Integer.parseInt(commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.12." + port));
            if(status == 2)  return "Не удалось совершить кабель тест";
            String firstpairstate = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.4."+port); // состояние первой пары
            String secondpairstate = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.5."+port);
            String pair1length = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.8."+port); // длина первой пары
            String pair2length = commutator.getResponse("1.3.6.1.4.1.171.12.58.1.1.1.9."+port); // длины второй пары
            return strCabletest(firstpairstate,secondpairstate,pair1length,pair2length);
        }catch(InterruptedException | NoSnmpAnswerException e){
            return "Не удалось совершить кабель тест";
        }
    }

    @Override
    public void dropCounters(Commutator commutator,int port) throws Exception{
        commutator.executeTelnetCommands( "clear counters ports "+port,"exit");
    }
}

//  .1.3.6.1.2.1.2.2.1.8.X - status porta des-3200
//   .1.3.6.1.2.1.17.7.1.4.3.1.1 - vlans
//
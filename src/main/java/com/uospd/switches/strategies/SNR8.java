package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;
import com.uospd.switches.interfaces.CableTestStrategy;
import com.uospd.switches.interfaces.DropCountersStrategy;

@CommutatorStrategyComponent("1.3.6.1.4.1.6339.1.1.1.48")
public class SNR8 implements CableTestStrategy, DropCountersStrategy{

    public String snmpCableTest(int port, Commutator commutator) throws NullPointerException{
        commutator.snmpSet(".1.3.6.1.4.1.6339.100.3.2.1.18." + port, 1);
        try{
            Thread.sleep(250);
            String output = commutator.getResponse(".1.3.6.1.4.1.6339.100.3.2.1.19." + port);
            //Обработка вывода
            output = output.replace("Interface Ethernet1/" + port + "", "Кабель тест").replace("Cable pairs", "Пара     ").replace("Cable status", "статус")
                    .replace("Length (meters)", "   длина(м)").replace("open", "обрыв  ")
                    .replace("(1, 2)", "(1, 2)      ").replace("(3, 6)", "(3, 6)      ")
                    .replace("short", "КЗ   ").replace("well", "  ОК   ");
            return output;
        }catch(Exception e){
            return "Произошла ошибка при выполнении кабель-теста";
        }
    }

    public String dropCounters(Commutator commutator,int port){
        return "clear counters interface ethernet 1/"+port;
    }
}

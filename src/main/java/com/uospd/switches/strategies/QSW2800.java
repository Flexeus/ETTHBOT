package com.uospd.switches.strategies;

import com.uospd.annotations.CommutatorStrategyComponent;
import com.uospd.switches.Commutator;


@CommutatorStrategyComponent({"1.3.6.1.4.1.27514.1.3.13", "1.3.6.1.4.1.27514.1.3.25.2" ,
                              "1.3.6.1.4.1.27514.1.1.1.49", "1.3.6.1.4.1.27514.1.1.1.48"})
public class QSW2800 extends QSW4610 {

    @Override
    public String dropCounters(Commutator commutator, int port){
        return "clear counters interface ethernet 1/"+port;
    }

}

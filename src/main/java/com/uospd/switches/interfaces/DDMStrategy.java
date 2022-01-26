package com.uospd.switches.interfaces;

import com.uospd.switches.Commutator;

public interface DDMStrategy extends CommutatorStrategy {
    String getDDMInfo(int port, Commutator commutator);
    default String ddmTemplate(String sfpTemp,String rxPower,String txPower){
        String result="DDM INFO: ";
        result += "\nТемпература SFP: " + sfpTemp;
        result += "\nRX Power(dBM): " + rxPower;
        result += "\nTX Power(dBM): " + txPower;
        return result;
    }
}

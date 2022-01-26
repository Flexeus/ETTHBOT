package com.uospd.switches.interfaces;

import com.uospd.switches.Commutator;

public interface CableTestStrategy extends CommutatorStrategy {
    String snmpCableTest(int port, Commutator commutator) throws NullPointerException;
}

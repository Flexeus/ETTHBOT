package com.uospd.switches.interfaces;

import com.uospd.switches.Commutator;

public interface DropCountersStrategy extends CommutatorStrategy{
    String dropCounters(Commutator commutator,int port);
}

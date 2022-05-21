package com.uospd.switches.interfaces;

import com.uospd.switches.Commutator;

import java.io.IOException;

public interface DropCountersStrategy extends CommutatorStrategy{
    void dropCounters(Commutator commutator,int port) throws Exception;
}

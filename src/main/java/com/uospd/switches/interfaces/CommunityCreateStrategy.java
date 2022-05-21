package com.uospd.switches.interfaces;

import com.uospd.switches.Commutator;

import java.io.IOException;

public interface CommunityCreateStrategy extends CommutatorStrategy{
    void writeCommunity(String community, Commutator commutator) throws Exception;
    void deleteCommunity(String community, Commutator commutator) throws Exception;
}

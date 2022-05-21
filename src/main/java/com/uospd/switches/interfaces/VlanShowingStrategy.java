package com.uospd.switches.interfaces;

import com.uospd.switches.Commutator;

import java.util.ArrayList;
import java.util.List;

public interface VlanShowingStrategy extends CommutatorStrategy{

  String showVlans(int port, Commutator commutator);

  default List<Integer> DecPorts(String in, int mnoj) {
    char[] inp = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    String[] out = {"0000", "0001", "0010", "0011", "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011", "1100", "1101", "1110", "1111"};
    int[] mnojs = {0, 1, 1025, 2050, 3073};
    List<Integer> res = new ArrayList<>();
    StringBuilder dec = new StringBuilder();
    for (int a = 0; a < in.length(); a++) {
      if (in.charAt(a) == ':') continue;
      for (int i = 0; i < inp.length; i++) if (in.charAt(a) == inp[i]) dec.append(out[i]);
    }
    for (int i = 0; i < dec.length(); i++)
      if (dec.charAt(i) == '1') res.add(mnojs[mnoj] + i);
    return res;
  }
}

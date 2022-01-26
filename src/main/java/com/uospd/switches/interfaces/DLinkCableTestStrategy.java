package com.uospd.switches.interfaces;

public interface DLinkCableTestStrategy extends CableTestStrategy {
    default String strCabletest(String fPairState,String sPairState,String fpairlength,String spairlength){
        if(fpairlength.equals("7") || sPairState.equals("7")){ // если кабеля нет, то метраж = 0
            fpairlength = "0";
            spairlength = "0";
        }
        String[] states ={"   OK               ","   Обрыв        ","   КЗ                ","Обрыв-КЗ     ","Перекрестные помехи","5","6","Нет кабеля ","Неизвестно"};
        return  "Кабель тест:\n-----------------------------------------------------\n" +
                "Пара         статус                  длина(м)\n" +
                "----------    -------------------       ----------------\n" +
                "1              "+states[Integer.parseInt(fPairState)]+"          "+fpairlength+"\n" +
                "2              "+states[Integer.parseInt(sPairState)]+"          "+spairlength;
    }
}

package com.uospd.utils;

public class TimeoutException extends Exception{
    int remainingSeconds;

    public TimeoutException(int remainingSeconds){
        super();
        this.remainingSeconds = remainingSeconds;
    }

    public int getRemainingSeconds(){
        return remainingSeconds;
    }
}

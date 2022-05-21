package com.uospd.utils;

public class TimeoutException extends Exception{
    long remainingSeconds;

    public TimeoutException(long remainingSeconds){
        super();
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds(){
        return remainingSeconds;
    }
}

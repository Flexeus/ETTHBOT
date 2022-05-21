package com.uospd.utils;

import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class AntifloodService{
    private Map<String, Date> antifloodMap = new HashMap<>(); // Callback:Timeout

    public void action(String action,long timeout) throws TimeoutException{
        Date date = antifloodMap.get(action);
        if(date != null){
            long remainingTimeout = timeout - Functions.getDateDiff(date, new Date(), TimeUnit.SECONDS);
            if(remainingTimeout > 0) throw new TimeoutException(remainingTimeout);
        }
        antifloodMap.put(action, new Date());
    }

}

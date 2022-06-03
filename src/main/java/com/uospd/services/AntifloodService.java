package com.uospd.services;

import com.uospd.utils.Functions;
import com.uospd.utils.TimeoutException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class AntifloodService{
    private Map<String, Date> antifloodMap = new HashMap<>(); // Callback:Timeout

    public void timeoutCheck(String action) throws TimeoutException{
        Date date = antifloodMap.get(action);
        if(date == null) return;
        long remainingTimeout = Functions.getDateDiff(new Date(), date, TimeUnit.SECONDS);
        if(remainingTimeout > 0) throw new TimeoutException(remainingTimeout);
    }

    public void action(String action,long timeout){
        antifloodMap.put(action, new Date(System.currentTimeMillis()+timeout*1000));
    }

}

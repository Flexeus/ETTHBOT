package com.uospd.utils;

import com.uospd.annotations.Callback;
import com.uospd.entityes.User;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class CallbackInvoker{
    private Map<CallbackInvocationMD,Date> callbackTimeoutCache = new HashMap<>();

    @Autowired @Qualifier("callbackControllers") @Getter @Setter
    private Map<String, Pair<Object,Method>> callbackMap;

    public boolean isCallbackAvailable(String callback){
        return callbackMap.containsKey(callback);
    }

    public String executeCallback(String callback,User user) throws TimeoutException{
        String[] args = callback.split(";");
        String callbackName = args[0];
        if(!isCallbackAvailable(callbackName)) throw new RuntimeException("Unexpected callback");
        Pair<Object, Method> objectMethodPair = callbackMap.get(callbackName);
        Object controllerObject = objectMethodPair.getFirst();
        Method callbackMethod = objectMethodPair.getSecond();
        Callback annotation = callbackMethod.getAnnotation(Callback.class);
        int callbackTimeout = annotation.timeout();
        CallbackInvocationMD callbackInvocationMD = new CallbackInvocationMD(annotation.globalTimeout() ? null : user, callback);
        if(callbackTimeout > 0 && callbackTimeoutCache.containsKey(callbackInvocationMD)){
            Date lastDate = callbackTimeoutCache.get(callbackInvocationMD);
            int delay = (int) (callbackTimeout-Functions.getDateDiff(lastDate, TimeUnit.SECONDS));
            if(delay>0 && !user.isAdmin()) throw new TimeoutException(delay);

        }
        String[] callbackArgs = Arrays.copyOfRange(args, 1, args.length);
        try{
            callbackMethod.invoke(controllerObject,user, callbackArgs);
            callbackTimeoutCache.put(callbackInvocationMD,new Date());
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    private static class CallbackInvocationMD{
        @Getter private final User user;
        @Getter private final String callback;
    }
}

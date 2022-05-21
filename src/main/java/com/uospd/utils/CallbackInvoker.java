package com.uospd.utils;

import com.uospd.entityes.User;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;

@Component
public class CallbackInvoker{

    @Autowired @Qualifier("callbackControllers") @Getter @Setter
    private Map<String, Pair<Object,Method>> callbackMap;

    public boolean isCallbackAvailable(String callback){
        return callbackMap.containsKey(callback);
    }

    public void executeCallback(String callback, CallbackQuery query, User user) throws TimeoutException{
        String[] args = callback.split(";");
        String callbackName = args[0];
        if(!isCallbackAvailable(callbackName)) throw new RuntimeException("Unexpected callback");
        Pair<Object, Method> objectMethodPair = callbackMap.get(callbackName);
        Object controllerObject = objectMethodPair.getFirst();
        Method callbackMethod = objectMethodPair.getSecond();
        String[] leftCallbackArgs = Arrays.copyOfRange(args, 1, args.length);
        try{
            Object[] objects = buildMethodInvokeArgs(user, leftCallbackArgs, query, callbackMethod);
            callbackMethod.invoke(controllerObject, objects);
        }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof TimeoutException timeoutException) throw timeoutException;
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private Object[] buildMethodInvokeArgs(User user, String[] leftCallbackArgs,CallbackQuery query, Method method){
        Parameter[] parameters = method.getParameters();
        Object[] methodArgs = new Object[parameters.length];
        for(int i = 0;i < parameters.length;i++){
            Class<?> type = parameters[i].getType();
            if(type == User.class) methodArgs[i] = user;
            else if(type == String[].class) methodArgs[i] = leftCallbackArgs;
            else if(type == CallbackQuery.class) methodArgs[i] = query;
            else methodArgs[i] = null;
        }
        return methodArgs;
    }
}

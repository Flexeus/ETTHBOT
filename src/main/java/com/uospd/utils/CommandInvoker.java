package com.uospd.utils;

import com.uospd.annotations.AdminController;
import com.uospd.annotations.Command;
import com.uospd.annotations.SuperAdminController;
import com.uospd.entityes.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public final class CommandInvoker{
    private final Map<User,String> commandListCache = new HashMap<>();

    @Autowired @Qualifier("commandControllers")
    private Map<String, Pair<Object,Method>> controllerMap;

    public String getCommandList(User user){
       // if(commandListCache.containsKey(user)) return commandListCache.get(user);
        StringBuilder stringBuilder = new StringBuilder(100);
        for(Map.Entry<String, Pair<Object, Method>> entry : controllerMap.entrySet()){
            Method method = entry.getValue().getSecond();
            Class<?> declaringClass = method.getDeclaringClass();
            if(declaringClass.isAnnotationPresent(AdminController.class) && !user.isAdmin()) continue;
            if(declaringClass.isAnnotationPresent(SuperAdminController.class) && !user.isSuperAdmin()) continue;
            Command annotation = method.getAnnotation(Command.class);
            String description = annotation.description();
            if(description.equals("")) continue;
            String[] allowedMembers = annotation.allowedMembers();
            if(!user.isAdmin() && allowedMembers.length != 0){
                boolean match = List.of(allowedMembers).contains(user.getGroup().getName());
                if(!match) continue; // если группы пользователя нет в списке разрешенных
            }
            if(stringBuilder.toString().contains(description)) continue;
            stringBuilder.append(description).append("\n");
        }
        String result = stringBuilder.toString().intern();
     //   commandListCache.put(user,result);
        return result;
    }

    public boolean isCommandAvailable(String command){
        return controllerMap.containsKey(command);
    }

    public String executeCommand(String cmd, User user, String[] args) {
        if(!isCommandAvailable(cmd)) return null;
        Pair<Object, Method> methodPair = controllerMap.get(cmd);
        Object controllerObject = methodPair.getFirst();
        Method commandMethod = methodPair.getSecond();
        if(controllerObject.getClass().isAnnotationPresent(AdminController.class) && !user.isAdmin()) return "Вы не уполномочены использовать эту команду";
        if(controllerObject.getClass().isAnnotationPresent(SuperAdminController.class) && !user.isSuperAdmin()) return "Вы не уполномочены использовать эту команду";
        Command annotation = commandMethod.getAnnotation(Command.class);
        String[] allowedMembers = annotation.allowedMembers();
        if(allowedMembers.length != 0){
            boolean match = List.of(allowedMembers).contains(user.getGroup().getName());
            if(!match && !user.isAdmin()) return "Вы не уполномочены использовать эту команду";
        }
        commandMethod.setAccessible(true);
        Object[] methodArgs = buildMethodInvokeArgs(user, args, commandMethod);
        try{
            commandMethod.invoke(controllerObject, methodArgs);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private Object[] buildMethodInvokeArgs(User user, String[] args, Method method){
        Parameter[] parameters = method.getParameters();
        Object[] methodArgs = new Object[parameters.length];
        for(int i = 0;i < parameters.length;i++){
            Class<?> type = parameters[i].getType();
            if(type == User.class) methodArgs[i] = user;
            else if(type == String[].class) methodArgs[i] = args;
            else methodArgs[i] = null;
        }
        return methodArgs;
    }


}

package com.uospd.services;

import com.uospd.UserNotFoundException;
import com.uospd.entityes.Group;
import com.uospd.entityes.User;
import com.uospd.repositories.GroupRepository;
import com.uospd.repositories.UserRepository;
import com.uospd.utils.Functions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final LoggingService logger;

    private final SortedMap<Integer,User> users = new TreeMap<>();
    private final Map<Integer,String> registrationRequests = new HashMap<>();
    private final Set<User> admins = new HashSet<>();

    @PostConstruct
    public void loadUsers(){
        users.clear();
        userRepository.findAll().forEach(user -> users.put(user.getId(),user));
        admins.addAll(users.values().stream().filter(User::isAdmin).collect(Collectors.toSet()));
        System.out.printf("Загружено %d пользователей. Из них %d администраторы\n",users.size(),admins.size());
    }


    public Map<Integer, String> getRegistrationRequests(){
        return registrationRequests;
    }

    public void deleteUser(User user){
        if(user == null) throw new IllegalArgumentException("User are null");
        logger.getUserLogFile(user.getId()).delete();
        users.remove(user.getId());
        userRepository.delete(user);
    }

    public void registerUser(User user, Integer groupId) {
        registrationRequests.remove(user.getId());
        users.put(user.getId(),user);
        user.setGroup(getGroupById(groupId));
        User newUser = userRepository.save(user);
        logger.writeUserLog(newUser,"зарегистрирован");
    }

    public User getUser(int id) throws UserNotFoundException{
        if(!users.containsKey(id)) throw new UserNotFoundException("User with id="+id+" not found");
        return users.get(id);
    }

    public boolean userExists(Integer id){
        return users.containsKey(id);
    }

    /**
     Searches for a user by name. If found returns id, otherwise -1
     @param name
     @return userid or -1 if user not found
     */
    public int getIdByName(String name){
        return users.values().stream().filter(x -> x.getName().contains(" ") && x.getName().substring(x.getName().indexOf(" ") + 1)
                .compareToIgnoreCase(name) == 0).map(User::getId)
                .findFirst().orElse(-1);
    }

    public List<User> getAllUsers(){
        return new ArrayList<>(users.values());
    }

    public void forEachUser(BiConsumer<Integer,User> biConsumer){
        users.forEach(biConsumer);
    }

    public int getUsersCount(){
        return users.size();
    }

    public String getRegisteredUsersList(){
        StringBuilder res = new StringBuilder("Зарегистрированные пользователи("+getUsersCount()+"):\n");
        users.values().stream().sorted()
                .forEach(z -> res.append(
                        String.format("%s(%s)\n",z.getName(),Functions.getAsLink(String.valueOf(z.getId()),"tg://user?id="+z.getId()))
                ));
        return res.toString();
    }

    public String getUsersConnections(){
        StringBuilder res = new StringBuilder("Подключения пользователей:\n");
        users.values().stream().filter(User::isConnectedToSwitch).sorted().forEachOrdered((k) ->
                res.append(k.getName()).append(": ").append(k.getSwitch().getIp()).append("\n")
        );
        return res.toString();
    }

    public String getUsersPhones(){
        if(users.isEmpty()) return "Нет зарегистрированных пользователей";
        StringBuilder res = new StringBuilder("Номера пользователей:\n");
        users.values().stream().filter(x->x!=null && x.getPhoneNumber()!=null).sorted().forEach(z -> res.append(z.getName()).append(": ").append(z.getPhoneNumber()).append("\n"));
        return res.toString();
    }

    public void saveUser(User user){
        userRepository.save(user);
    }

    public Set<User> getAdminList(){
       return Collections.unmodifiableSet(admins);
    }

    public List<Group> getAllGroups(){
        return groupRepository.findAll();
    }

    public Group getGroupById(Integer id){
        return groupRepository.findById(id).orElseThrow( () -> new EntityNotFoundException("group with id=" + id + " not found"));
    }

}

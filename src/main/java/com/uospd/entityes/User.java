package com.uospd.entityes;

import com.uospd.switches.Commutator;
import lombok.*;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "bot_users",catalog = "telebot")
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class User implements Comparable<User>{
    @Id @Setter(value = AccessLevel.PRIVATE)
    @Column(name = "SenderID",nullable = false,unique = true)
    private int id;

    @JoinColumn(name = "group_id",nullable = false)
    @ManyToOne
    private Group group;

    @Column(name = "Name",updatable = false,nullable = false,unique = true)
    private String name;

    @Column(name = "PhoneNumber",unique = true)
    private String phoneNumber;

    private transient boolean banned;
    private transient String lastCMD;
    private transient Date lastCMDDate  = new Date();
    @Transient private transient Commutator commutator;

    public User(Integer id,String Name){
        this.id = id;
        this.name = Name;
    }

    public User(Integer id, String Name, Group group, String phoneNumber){
        this.group = group;
        this.id = id;
        this.name = Name;
        this.phoneNumber = phoneNumber;
    }
    public boolean isAdmin(){
        return this.isSuperAdmin() || id == 435734550L;
    }

    public boolean isSuperAdmin(){
        return id == 810937833L;
    }

    public void setCommutator(Commutator commutator){
        this.commutator = commutator;
        setLastCMD("");
    }

    public Commutator getSwitch(){
        if(!isConnectedToSwitch()) throw new IllegalStateException(name+ " not connected to switch");
        return commutator;
    }

    public boolean isConnectedToSwitch(){
        return commutator != null;
    }

    public void setLastCMD(String cmd){
        this.lastCMD = cmd;
        lastCMDDate = new Date();
    }

    public String getLastCMD() {
        if(lastCMD == null) return "";
        return lastCMD;
    }

    @Override
    public int compareTo(User o) {
        String OFirstname = o.getName().split(" ")[0];
        String ThisFirstname = this.getName().split(" ")[0];
        return ThisFirstname.compareTo(OFirstname);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", banned=" + banned +
                ", group='" + group.getName() + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

}

package com.uospd.entityes;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@Table(name = "bot_groups",catalog = "telebot")
@NoArgsConstructor
public class Group{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false,unique = true)
    private String name;

//    @OneToMany(mappedBy = "group")
//    private List<User> users;

    @Column(name = "clear_counters")
    private boolean canClearCounters;

    @Column(name = "connect_to_aggregation")
    private boolean canConnectToAggregation;

    @Column(name = "watch_uplinks")
    private boolean canWatchUplinks;

    public boolean canClearCounters(){ return canClearCounters; }
    public boolean canWatchUplinks(){ return canWatchUplinks; }
    public boolean canConnectToAggregation(){ return canConnectToAggregation; }

    @Override public String toString(){
        return "Group{" +
                ", name=" + name +
                ", canClearCounters=" + canClearCounters +
                ", canConnectToAggregation=" + canConnectToAggregation +
                ", canWatchUplinks=" + canWatchUplinks +
                '}';
    }
}

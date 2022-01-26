package com.uospd.entityes;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "accidents",catalog = "telebot")
@Getter
@NoArgsConstructor
public class Accident{
    @Id
    private String ip;
    private int state;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ChangeTime")
    private Date date;
    private String street;
    private String home;
}

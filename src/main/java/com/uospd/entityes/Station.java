package com.uospd.entityes;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Table(name = "station",catalog = "telebot")
@Entity
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class Station implements Serializable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String type;
    private String street;
    private String home;
    private int number;

    @Override public String toString(){
        return "Station{" +
                ", type='" + type + '\'' +
                ", street='" + street + '\'' +
                ", home='" + home + '\'' +
                ", number=" + number +
                '}';
    }
}

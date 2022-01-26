package com.uospd.switches;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "models",catalog = "telebot")
@NoArgsConstructor
@Getter
@ToString
public class CommutatorModel{

    @Id
    @Column(name = "ObjectID",nullable = false)
    private String OID;

    @Column(name = "Realmodel",nullable = false)
    private String model;

    @Column(name = "PortCount",nullable = false)
    private Integer portsCount;

    @Column(name = "UplinkCount",nullable = false)
    private Integer upLinkCount;

    @Column(name = "FirstPortOid",nullable = false)
    private Integer firstPortID;

    @Column(name = "firstUplinkOid",nullable = false)
    private Integer firstUpLinkID;

    @Column(name = "agregation",nullable = false)
    private boolean agregation;
}

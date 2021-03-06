package com.uospd.services;

import com.uospd.entityes.Station;
import com.uospd.repositories.CommutatorRepository;
import com.uospd.repositories.StationRepository;
import com.uospd.switches.*;
import com.uospd.switches.exceptions.ConnectException;
import com.uospd.switches.exceptions.InvalidDatabaseOIDException;
import com.uospd.switches.exceptions.NoSnmpAnswerException;
import com.uospd.switches.exceptions.UnsupportedCommutatorException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommutatorService {
    private final CommutatorRepository commutatorRepository;
    private final CommutatorConnector commutatorConnector;
    private final StationRepository stationRepository;

    @Value("${rwcommunity}") private String rwCommunity;

    public List<Station> getStations(){
        return stationRepository.findAll();
    }

    public List<Commutator> findStation(String type, int number){
        Optional<Station> optional = stationRepository.findByTypeAndNumber(type.toUpperCase(), number);
        if(optional.isEmpty()) return Collections.emptyList();
        Station station = optional.get();
        List<Commutator> commutators = getAllByAddress(station.getStreet(), station.getHome());
        if(commutators == null || commutators.isEmpty()) return Collections.emptyList();
        else return commutators;
    }

    public List<Commutator> getAllByAddress(String street, String home){
        return commutatorRepository.findAllByStreetContainingAndHomeEquals(street,home);
    }

    public List<Commutator> getAllByStreet(String street){
        return commutatorRepository.findAllByAddress(street);
    }

    public Commutator getCommutator(String ip) throws UnsupportedCommutatorException{
        return commutatorRepository.findByIp(ip).orElseThrow(() ->new UnsupportedCommutatorException("Commutator with such ip address not found in database"));
    }

    public Commutator connect(String ip) throws UnsupportedCommutatorException, ConnectException, InvalidDatabaseOIDException, NoSnmpAnswerException{
        return commutatorConnector.getFeaturedCommutator(getCommutator(ip), rwCommunity);
    }


    public Commutator connect(Commutator commutator,String community) throws UnsupportedCommutatorException, ConnectException, InvalidDatabaseOIDException, NoSnmpAnswerException{
        return commutatorConnector.getFeaturedCommutator(commutator, community);
    }

    public void disconnect(Commutator commutator){
        if(commutator == null) return;
        commutatorConnector.disconnect(commutator);
    }

    public String getSwitchPoolInfo(){
        return commutatorConnector.getSwitchPoolInfo();
    }

    public String getSwitchInfo(Commutator commutator) {
        StringBuilder info = new StringBuilder();
        info.append("IP:").append(commutator.getIp())
                .append(". ??????????: ").append(commutator.getStreet()).append(",").append(commutator.getHome()).append('.');
        if(commutator.getPorch() != 0) info.append(" ??????????????: " ).append(commutator.getPorch());
        info.append("(").append(commutator.getVertical()).append(")");
        if (commutator.model().isAgregation()) info.append("\nHostname: ").append(commutator.getHostName());
        info.append("\n????????????: ").append(commutator.model().getModel());
        return info.toString();
    }

    public List<Commutator> getAllCommutators(){
        return commutatorRepository.findAll();
    }

}

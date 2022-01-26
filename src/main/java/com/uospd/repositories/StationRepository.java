package com.uospd.repositories;

import com.uospd.entityes.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station,Integer>{
    Optional<Station> findByTypeAndNumber(String type,int number);
}

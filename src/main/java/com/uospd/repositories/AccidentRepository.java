package com.uospd.repositories;

import com.uospd.entityes.Accident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccidentRepository extends JpaRepository<Accident,String>{
    List<Accident> findAllByState(int state);
}

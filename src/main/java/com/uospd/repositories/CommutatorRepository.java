package com.uospd.repositories;

import com.uospd.switches.Commutator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommutatorRepository extends JpaRepository<Commutator,Integer> {
    Optional<Commutator> findByIp(String ip);
    List<Commutator> findAllByStreetContainingAndHomeEquals(String street, String home);
    @Query(value = "SELECT c from Commutator c WHERE CONCAT(c.street, ' ', c.home) LIKE %?1%")
    List<Commutator> findAllByAddress(String street);

}

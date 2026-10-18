package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Chemistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChemistryRepository extends JpaRepository<Chemistry, Long> {

    Optional<Chemistry> findByMemberAAndMemberB(String memberA, String memberB);
}

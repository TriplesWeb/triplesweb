package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ChemistryGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChemistryGroupRepository extends JpaRepository<ChemistryGroup, Long> {
    Optional<ChemistryGroup> findByMemberKey(String memberKey);
}

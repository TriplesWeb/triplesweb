package com.example.triplesweb.repository;


import com.example.triplesweb.entity.Members;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface MemberRepository extends CrudRepository<Members,Long> {
    Optional<Members> findByUserId(String userId);
    Optional<Members> findByEmail(String email);
    boolean existsByUserId(String userId);
    boolean existsByEmail(String email);
}

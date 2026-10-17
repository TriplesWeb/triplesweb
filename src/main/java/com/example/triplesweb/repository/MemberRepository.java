package com.example.triplesweb.repository;


import com.example.triplesweb.entity.Members;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

/**
 * 회원({@link Members}) CRUD 저장소.
 * userId·email 조회 및 중복 존재 여부({@code existsBy*}) 쿼리를 제공한다.
 */
public interface MemberRepository extends CrudRepository<Members,Long> {
    Optional<Members> findByUserId(String userId);
    Optional<Members> findByEmail(String email);
    boolean existsByUserId(String userId);
    boolean existsByEmail(String email);
}

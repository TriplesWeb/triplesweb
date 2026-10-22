package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * {@link Unit} 엔티티 JPA 저장소.
 * 기본 CRUD는 {@link JpaRepository}를 사용한다.
 */
public interface UnitRepository extends JpaRepository<Unit, Long> {

    /** One collection fetch avoids per-unit/member queries and multiple-bag joins. */
    @Query("""
            select distinct u from Unit u
            left join fetch u.unitMembers member
            left join fetch member.artist
            order by u.formationDate, u.unitId
            """)
    List<Unit> findAllForUnitPage();

    /** 목록/탭: 결성일 오름차순, 동일 결성일은 id 순 */
    List<Unit> findByVisibleTrueOrderByFormationDateAscUnitIdAsc();
}

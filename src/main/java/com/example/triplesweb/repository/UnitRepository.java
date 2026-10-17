package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link Unit} 엔티티 JPA 저장소.
 * 기본 CRUD는 {@link JpaRepository}를 사용한다.
 */
public interface UnitRepository extends JpaRepository<Unit, Long> {
}

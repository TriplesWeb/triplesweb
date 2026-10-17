package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 일정({@link Schedule}) JPA 저장소.
 * 기본 CRUD는 {@link JpaRepository}를 사용한다.
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}

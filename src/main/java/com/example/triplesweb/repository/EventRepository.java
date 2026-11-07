package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByEventName(String eventName);

    /** 목록용: 섹션·회차를 함께 로드한다. */
    @Query("""
            select distinct e from Event e
            left join fetch e.section
            left join fetch e.stops
            """)
    List<Event> findAllWithSectionAndStops();
}

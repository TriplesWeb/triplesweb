package com.example.triplesweb.repository;

import com.example.triplesweb.entity.CalendarDecoration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarDecorationRepository extends JpaRepository<CalendarDecoration, Long> {
    List<CalendarDecoration> findAllByOrderByEventDateAscIdAsc();
}

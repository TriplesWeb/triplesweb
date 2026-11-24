package com.example.triplesweb.repository;

import com.example.triplesweb.entity.EventSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventSectionRepository extends JpaRepository<EventSection, Long> {
    List<EventSection> findAllByOrderBySortOrderAscSectionIdAsc();
}

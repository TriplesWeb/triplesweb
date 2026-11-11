package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findByEvent_EventId(Long eventId);

    /** 목록용: 섹션·이벤트를 함께 로드한다. */
    @Query("""
            select distinct s from Shop s
            left join fetch s.section
            left join fetch s.event e
            left join fetch e.stops
            """)
    List<Shop> findAllWithSectionAndEvent();

    @Query("""
            select s from Shop s
            left join fetch s.section
            left join fetch s.event e
            left join fetch e.stops
            where s.id = :id
            """)
    Optional<Shop> findDetailById(long id);
}

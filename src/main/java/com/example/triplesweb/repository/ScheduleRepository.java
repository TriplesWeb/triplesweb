package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 일정({@link Schedule}) JPA 저장소.
 * 기본 CRUD는 {@link JpaRepository}를 사용한다.
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @Query("""
            select distinct s from Schedule s
            left join fetch s.artists
            order by s.start asc, s.id asc
            """)
    List<Schedule> findAllWithArtists();

    @Query("""
            select distinct s from Schedule s
            join fetch s.artists artist
            where artist.artistId = :artistId
            order by s.start asc, s.id asc
            """)
    List<Schedule> findByArtistId(@Param("artistId") Long artistId);
}

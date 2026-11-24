package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일정({@link Schedule}) JPA 저장소.
 * 기본 CRUD는 {@link JpaRepository}를 사용한다.
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findTop5ByArtists_ArtistIdOrderByStartDescIdDesc(Long artistId);
    @Query("select distinct s from Schedule s left join fetch s.artists")
    List<Schedule> findAllWithArtists();

    @Query("""
            select distinct s from Schedule s left join fetch s.artists
            where s.start < :exclusiveEnd and (s.end >= :start or (s.end is null and s.start >= :start))
              and (:artist is null or exists (
                  select matched.id from Schedule matched join matched.artists a
                  where matched = s and a.name = :artist))
            order by s.start, s.id
            """)
    List<Schedule> findOverlapping(@Param("start") LocalDate start,
                                   @Param("exclusiveEnd") LocalDate exclusiveEnd,
                                   @Param("artist") String artist);

    @Query("""
            select s.start from Schedule s
            where s.start is not null and (s.end >= :today or (s.end is null and s.start >= :today))
            order by s.start, s.id
            """)
    List<LocalDate> findUpcomingStartDates(@Param("today") LocalDate today, Pageable limit);

    @Query("select s.start from Schedule s where s.id = :id")
    Optional<LocalDate> findStartById(@Param("id") Long id);

    @Query("select distinct s from Schedule s join s.artists a where a.name in :names")
    List<Schedule> findDistinctByArtistNames(@Param("names") Collection<String> names);
}

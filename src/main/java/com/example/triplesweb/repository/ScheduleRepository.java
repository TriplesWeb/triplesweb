package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 일정({@link Schedule}) JPA 저장소.
 * 기본 CRUD는 {@link JpaRepository}를 사용한다.
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findTop5ByArtists_ArtistIdOrderByStartDescIdDesc(Long artistId);
    @Query("select distinct s from Schedule s left join fetch s.artists")
    List<Schedule> findAllWithArtists();

    @Query("select distinct s from Schedule s join s.artists a where a.name in :names")
    List<Schedule> findDistinctByArtistNames(@Param("names") Collection<String> names);
}

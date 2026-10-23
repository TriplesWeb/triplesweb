package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 아티스트({@link Artist}) JPA 저장소.
 * artistId·영문명 중복 검사 및 {@link #findByEngName(String)} 조회를 제공한다.
 */
public interface ArtistRepository extends JpaRepository<Artist, Long> {
    boolean existsByArtistId(Long artistId);

    boolean existsByEngName(String engName);

    Optional<Artist> findByEngName(String engName);

    Optional<Artist> findByName(String name);

    @Query("""
            SELECT a FROM Artist a
            LEFT JOIN FETCH a.affiliatedUnit au
            LEFT JOIN FETCH au.unit
            WHERE a.artistId = :artistId
            """)
    Optional<Artist> findByIdWithUnits(@Param("artistId") Long artistId);

    @Query("""
            SELECT DISTINCT a FROM Artist a
            LEFT JOIN FETCH a.affiliatedUnit au
            LEFT JOIN FETCH au.unit
            ORDER BY a.artistId
            """)
    List<Artist> findAllWithUnits();
}

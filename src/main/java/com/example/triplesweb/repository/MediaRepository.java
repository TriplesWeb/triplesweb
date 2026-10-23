package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Page<Media> findByTabOrderBySortOrderAscIdAsc(String tab, Pageable pageable);

    List<Media> findByTabOrderBySortOrderAscIdAsc(String tab);

    long countByTab(String tab);

    Optional<Media> findByTabAndYoutubeIdIgnoreCase(String tab, String youtubeId);

    @Query("""
            select distinct m from Media m
            join m.artists artist
            where artist.artistId = :artistId
            order by m.sortOrder asc, m.id asc
            """)
    List<Media> findByArtistId(@Param("artistId") Long artistId);

    Optional<Media> findByYoutubeIdIgnoreCase(String youtubeId);

    void deleteByTabAndYoutubeIdIgnoreCase(String tab, String youtubeId);

    @Query("select coalesce(min(m.sortOrder), 0) from Media m where m.tab = :tab")
    long minSortOrder(@Param("tab") String tab);

    @Query("select coalesce(max(m.sortOrder), 0) from Media m where m.tab = :tab")
    long maxSortOrder(@Param("tab") String tab);
}

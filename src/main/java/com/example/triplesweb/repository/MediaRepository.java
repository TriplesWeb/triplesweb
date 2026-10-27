package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Page<Media> findByTabOrderBySortOrderAscIdAsc(String tab, Pageable pageable);

    List<Media> findByTabOrderBySortOrderAscIdAsc(String tab);

    List<Media> findDistinctByArtists_ArtistIdOrderBySortOrderAscIdAsc(Long artistId);
    List<Media> findDistinctByArtists_ArtistIdOrderBySortOrderAscIdAsc(Long artistId, Pageable pageable);

    @EntityGraph(attributePaths = "artists")
    @Query("select distinct m from Media m order by m.sortOrder asc, m.id asc")
    List<Media> findAllWithArtists();

    long countByTab(String tab);

    Optional<Media> findByTabAndYoutubeIdIgnoreCase(String tab, String youtubeId);

    Optional<Media> findByYoutubeIdIgnoreCase(String youtubeId);

    void deleteByTabAndYoutubeIdIgnoreCase(String tab, String youtubeId);

    @Query("select coalesce(min(m.sortOrder), 0) from Media m where m.tab = :tab")
    long minSortOrder(@Param("tab") String tab);

    @Query("select coalesce(max(m.sortOrder), 0) from Media m where m.tab = :tab")
    long maxSortOrder(@Param("tab") String tab);
}

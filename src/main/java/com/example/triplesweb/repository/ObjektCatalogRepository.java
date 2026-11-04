package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ObjektCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObjektCatalogRepository extends JpaRepository<ObjektCatalog, Long> {
    List<ObjektCatalog> findByArtistIgnoreCaseOrderByNumberValueAsc(String artist);

    void deleteByArtistIgnoreCase(String artist);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ObjektCatalog c where c.artist is not null and lower(c.artist) <> lower(:keep)")
    int deleteWhereArtistIsNot(@Param("keep") String keep);
}

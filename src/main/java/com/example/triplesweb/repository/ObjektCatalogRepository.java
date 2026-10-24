package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ObjektCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObjektCatalogRepository extends JpaRepository<ObjektCatalog, Long> {
    List<ObjektCatalog> findByArtistIgnoreCaseOrderByNumberValueAsc(String artist);

    void deleteByArtistIgnoreCase(String artist);
}

package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ObjektCatalogStore {
    private final ObjektCatalogRepository repository;

    public ObjektCatalogStore(ObjektCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void replace(String artist, List<ObjektCatalog> rows) {
        for (ObjektCatalog row : rows) {
            if (row.getArtist() == null || row.getArtist().isBlank()) {
                row.setArtist(artist);
            }
        }
        repository.deleteAllInBatch();
        repository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<ObjektCatalog> load(String artist) {
        List<ObjektCatalog> rows = repository.findByArtistIgnoreCaseOrderByNumberValueAsc(artist);
        if (!rows.isEmpty()) return rows;
        return repository.findAll(Sort.by("numberValue"));
    }
}

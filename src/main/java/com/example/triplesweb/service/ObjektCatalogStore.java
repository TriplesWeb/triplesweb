package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
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
    public void retainOnly(String artist) {
        repository.deleteWhereArtistIsNot(keptArtist(artist));
    }

    private static String keptArtist(String artist) {
        return artist == null || artist.isBlank() ? "triples" : artist.trim();
    }

    @Transactional
    public int fillMissingChainBackImages(List<ObjektCatalog> rows) {
        int updated = 0;
        for (ObjektCatalog row : rows) {
            updated += repository.fillMissingChainBackImage(row.getCollectionId(), row.getBackImage());
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public List<ObjektCatalog> load(String artist) {
        return repository.findByArtistIgnoreCaseOrderByNumberValueAsc(keptArtist(artist));
    }
}

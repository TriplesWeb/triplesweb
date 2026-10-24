package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ObjektCatalogStore {
    private final ObjektCatalogRepository repository;

    public ObjektCatalogStore(ObjektCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void replace(String artist, List<ObjektCatalog> rows) {
        Map<String, ObjektCatalog> existingByCollectionId = new HashMap<>();
        for (ObjektCatalog existing : repository.findAll()) {
            if (existing.getCollectionId() != null && !existing.getCollectionId().isBlank()) {
                existingByCollectionId.put(existing.getCollectionId(), existing);
            }
        }

        Set<String> incomingCollectionIds = new HashSet<>();
        List<ObjektCatalog> newRows = new ArrayList<>();
        for (ObjektCatalog row : rows) {
            if (row.getArtist() == null || row.getArtist().isBlank()) {
                row.setArtist(artist);
            }
            String collectionId = row.getCollectionId();
            if (collectionId == null || collectionId.isBlank()) continue;
            incomingCollectionIds.add(collectionId);

            ObjektCatalog existing = existingByCollectionId.get(collectionId);
            if (existing == null) {
                newRows.add(row);
            } else {
                copyInto(existing, row);
            }
        }

        // 전체 삭제 후 재삽입하면 갱신 주기마다 모든 오브젝트의 INSERT 로그가 발생한다.
        // 기존 엔티티는 변경된 필드만 dirty checking으로 UPDATE하고, 새 행만 INSERT한다.
        List<ObjektCatalog> removedRows = existingByCollectionId.values().stream()
                .filter(row -> !incomingCollectionIds.contains(row.getCollectionId()))
                .toList();
        if (!removedRows.isEmpty()) repository.deleteAll(removedRows);
        if (!newRows.isEmpty()) repository.saveAll(newRows);
    }

    private static void copyInto(ObjektCatalog target, ObjektCatalog source) {
        target.setExternalId(source.getExternalId());
        target.setCreatedAt(source.getCreatedAt());
        target.setSlug(source.getSlug());
        target.setSeason(source.getSeason());
        target.setMember(source.getMember());
        target.setArtist(source.getArtist());
        target.setCollectionNo(source.getCollectionNo());
        target.setObjektClass(source.getObjektClass());
        target.setThumbnailImage(source.getThumbnailImage());
        target.setFrontImage(source.getFrontImage());
        target.setBackImage(source.getBackImage());
        target.setBackgroundColor(source.getBackgroundColor());
        target.setTextColor(source.getTextColor());
        target.setOnOffline(source.getOnOffline());
        target.setHasAudio(source.getHasAudio());
        target.setOriginalFrontImage(source.getOriginalFrontImage());
        target.setOriginalBackImage(source.getOriginalBackImage());
        target.setFrontMedia(source.getFrontMedia());
        target.setBandImageUrl(source.getBandImageUrl());
        target.setSearchText(source.getSearchText());
        target.setSeasonKey(source.getSeasonKey());
        target.setClassKey(source.getClassKey());
        target.setNumberValue(source.getNumberValue());
    }

    @Transactional(readOnly = true)
    public List<ObjektCatalog> load(String artist) {
        List<ObjektCatalog> rows = repository.findByArtistIgnoreCaseOrderByNumberValueAsc(artist);
        if (!rows.isEmpty()) return rows;
        return repository.findAll(Sort.by("numberValue"));
    }
}

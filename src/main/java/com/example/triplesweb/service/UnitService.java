package com.example.triplesweb.service;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 유닛 목록·상세 조회. 부모/자식 앨범 병합 포함.
 */
@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final AlbumRepository albumRepository;

    @Transactional(readOnly = true)
    public List<Unit> listVisible() {
        List<Unit> units = unitRepository.findByVisibleTrueOrderByFormationDateAscUnitIdAsc();
        for (Unit unit : units) {
            hydrateForPage(unit);
        }
        return units;
    }

    @Transactional(readOnly = true)
    public UnitDetail getDetail(long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유닛이 존재하지 않습니다. id=" + unitId));
        hydrateForPage(unit);
        return new UnitDetail(unit, unit.getDisplayAlbums());
    }

    private void hydrateForPage(Unit unit) {
        unit.setDisplayAlbums(mergeAlbums(unit));
        if (unit.getUnitMembers() != null) {
            for (UnitMember member : unit.getUnitMembers()) {
                if (member.getArtist() != null) {
                    member.getArtist().getName();
                    member.getArtist().getProfileImage();
                    member.getArtist().getArtistId();
                }
            }
        }
    }

    private List<Album> mergeAlbums(Unit unit) {
        Map<Long, Album> byId = new LinkedHashMap<>();
        addAlbums(byId, albumRepository.findByUnit_UnitId(unit.getUnitId()));
        if (unit.getParentUnit() != null && unit.getParentUnit().getUnitId() != null) {
            addAlbums(byId, albumRepository.findByUnit_UnitId(unit.getParentUnit().getUnitId()));
        }
        if (unit.getSubUnits() != null) {
            for (Unit sub : unit.getSubUnits()) {
                if (sub.getUnitId() != null) {
                    addAlbums(byId, albumRepository.findByUnit_UnitId(sub.getUnitId()));
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static void addAlbums(Map<Long, Album> byId, List<Album> albums) {
        if (albums == null) return;
        for (Album album : albums) {
            if (album != null && album.getAlbumId() != null) {
                byId.putIfAbsent(album.getAlbumId(), album);
            }
        }
    }

    public record UnitDetail(Unit unit, List<Album> albums) {}
}

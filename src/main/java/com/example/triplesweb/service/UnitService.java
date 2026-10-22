package com.example.triplesweb.service;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 유닛 목록·상세 조회. 부모/자식 앨범 병합 포함.
 */
@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public List<Unit> listVisible() {
        return unitRepository.findByVisibleTrueOrderByUnitIdAsc();
    }

    @Transactional(readOnly = true)
    public UnitDetail getDetail(long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유닛이 존재하지 않습니다. id=" + unitId));

        List<Album> albums = new ArrayList<>(unit.getAlbums());

        if (unit.getParentUnit() != null && unit.getParentUnit().getAlbums() != null) {
            for (Album parentAlbum : unit.getParentUnit().getAlbums()) {
                if (!albums.contains(parentAlbum)) {
                    albums.add(parentAlbum);
                }
            }
        }
        if (unit.getSubUnits() != null && !unit.getSubUnits().isEmpty()) {
            for (Unit subUnit : unit.getSubUnits()) {
                for (Album subAlbum : subUnit.getAlbums()) {
                    if (!albums.contains(subAlbum)) {
                        albums.add(subAlbum);
                    }
                }
            }
        }

        // 멤버·이미지 등 지연 로딩을 뷰 렌더 전에 강제
        if (unit.getUnitMembers() != null) {
            unit.getUnitMembers().size();
        }

        return new UnitDetail(unit, albums);
    }

    public record UnitDetail(Unit unit, List<Album> albums) {}
}

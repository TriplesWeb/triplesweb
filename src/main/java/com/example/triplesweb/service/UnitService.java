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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        // Hidden containers/children still participate in album inheritance.
        List<Unit> allUnits = unitRepository.findAllForUnitPage();
        List<Unit> visible = allUnits.stream().filter(Unit::isVisible).toList();
        if (visible.isEmpty()) return visible;

        Map<Long, List<Long>> childIds = new LinkedHashMap<>();
        for (Unit unit : allUnits) {
            if (unit.getParentUnit() != null) {
                childIds.computeIfAbsent(unit.getParentUnit().getUnitId(), ignored -> new ArrayList<>())
                        .add(unit.getUnitId());
            }
        }
        Map<Long, List<Album>> albumsByUnit = new LinkedHashMap<>();
        var unitIds = allUnits.stream().map(Unit::getUnitId).toList();
        for (Album album : albumRepository.findByUnit_UnitIdInOrderByReleaseDateAscAlbumIdAsc(unitIds)) {
            albumsByUnit.computeIfAbsent(album.getUnit().getUnitId(), ignored -> new ArrayList<>()).add(album);
        }
        for (Unit unit : visible) {
            Map<Long, Album> merged = new LinkedHashMap<>();
            addAlbums(merged, albumsByUnit.get(unit.getUnitId()));
            if (unit.getParentUnit() != null) addAlbums(merged, albumsByUnit.get(unit.getParentUnit().getUnitId()));
            for (Long childId : childIds.getOrDefault(unit.getUnitId(), List.of())) {
                addAlbums(merged, albumsByUnit.get(childId));
            }
            unit.setDisplayAlbums(sortedByReleaseDate(merged.values()));
            sortMembersByArtistId(unit);
        }
        return visible;
    }

    /** 아카이브 폴더 분류용. 멤버만 필요하고 앨범 병합은 생략한다. */
    @Transactional(readOnly = true)
    public List<Unit> listVisibleForArchive() {
        List<Unit> visible = unitRepository.findAllForUnitPage().stream().filter(Unit::isVisible).toList();
        for (Unit unit : visible) {
            sortMembersByArtistId(unit);
        }
        return visible;
    }

    /**
     * 멤버/아카이브 편집용 소속 유닛 체크 목록.
     * 숨김 유닛·상위 컨테이너(예: msnz)는 제외. Mustache {@code unitCheckboxOptions}.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUnitCheckboxOptions() {
        return filterCheckboxEditable(unitRepository.findAllForUnitPage()).stream()
                .sorted(Comparator.comparingLong(unit -> unit.getUnitId() != null ? unit.getUnitId() : 0L))
                .map(unit -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("unitId", unit.getUnitId());
                    row.put("unitName", unit.getUnitName());
                    String membersCsv = checkboxMemberNames(unit);
                    if (!membersCsv.isEmpty()) {
                        row.put("membersCsv", membersCsv);
                    }
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** 체크 UI에 노출되는 유닛 id. 아티스트 저장 시 UI 밖 소속은 건드리지 않는다. */
    @Transactional(readOnly = true)
    public Set<Long> checkboxEditableUnitIds() {
        // 멤버 fetch 조인 없이 id만 본다. 편집 트랜잭션과 findAllForUnitPage가 섞이면 판정이 어긋날 수 있다.
        return filterCheckboxEditable(unitRepository.findAll()).stream()
                .map(Unit::getUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Unit> filterCheckboxEditable(List<Unit> all) {
        Set<Long> parentIds = all.stream()
                .map(Unit::getParentUnit)
                .filter(Objects::nonNull)
                .map(Unit::getUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return all.stream()
                .filter(Unit::isVisible)
                .filter(unit -> unit.getUnitId() == null || !parentIds.contains(unit.getUnitId()))
                .collect(Collectors.toList());
    }

    private static String checkboxMemberNames(Unit unit) {
        if (unit.getUnitMembers() == null || unit.getUnitMembers().isEmpty()) return "";
        return unit.getUnitMembers().stream()
                .map(UnitMember::getArtist)
                .filter(Objects::nonNull)
                .map(artist -> artist.getName() == null ? "" : artist.getName().trim())
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(","));
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
        sortMembersByArtistId(unit);
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

    /** join fetch 결과는 DB 삽입 순일 수 있어 artistId(S순)로 고정한다. */
    private static void sortMembersByArtistId(Unit unit) {
        if (unit.getUnitMembers() == null || unit.getUnitMembers().size() < 2) return;
        unit.getUnitMembers().sort(Comparator.comparingLong(member ->
                member.getArtist() != null && member.getArtist().getArtistId() != null
                        ? member.getArtist().getArtistId()
                        : 0L));
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
        return sortedByReleaseDate(byId.values());
    }

    /** 부모/자식 병합 후 발매일 오름차순(동일 시 albumId)으로 고정한다. */
    private static List<Album> sortedByReleaseDate(Iterable<Album> albums) {
        List<Album> sorted = new ArrayList<>();
        for (Album album : albums) {
            if (album != null) sorted.add(album);
        }
        sorted.sort(Comparator
                .comparing((Album a) -> a.getReleaseDate() != null ? a.getReleaseDate() : "", Comparator.nullsLast(String::compareTo))
                .thenComparing(a -> a.getAlbumId() != null ? a.getAlbumId() : 0L));
        return sorted;
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

package com.example.triplesweb.service;

import com.example.triplesweb.dto.UnitAdminDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnitAdminService {

    private final UnitRepository unitRepository;
    private final ArtistRepository artistRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public UnitAdminDto get(long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유닛이 존재하지 않습니다. id=" + unitId));
        if (unit.getUnitMembers() != null) unit.getUnitMembers().size();
        if (unit.getParentUnit() != null) unit.getParentUnit().getUnitName();
        return toDto(unit);
    }

    @Transactional(readOnly = true)
    public List<UnitAdminDto.UnitOption> listUnitOptions() {
        return unitRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(
                        a.getUnitId() != null ? a.getUnitId() : 0L,
                        b.getUnitId() != null ? b.getUnitId() : 0L))
                .map(u -> UnitAdminDto.UnitOption.builder()
                        .unitId(u.getUnitId())
                        .unitName(u.getUnitName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UnitAdminDto.MemberOption> listArtistOptions() {
        return artistRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(
                        a.getArtistId() != null ? a.getArtistId() : 0L,
                        b.getArtistId() != null ? b.getArtistId() : 0L))
                .map(a -> UnitAdminDto.MemberOption.builder()
                        .artistId(a.getArtistId())
                        .name(a.getName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public UnitAdminDto create(
            String unitName,
            String smallName,
            String formationDate,
            String debutDate,
            boolean visible,
            Long parentUnitId,
            List<Long> artistIds,
            MultipartFile image,
            String imageUrl
    ) {
        Unit unit = new Unit();
        applyFields(unit, null, unitName, smallName, formationDate, debutDate, visible, parentUnitId, artistIds, image, imageUrl, false);
        return toDto(unitRepository.save(unit));
    }

    @Transactional
    public UnitAdminDto update(
            long unitId,
            String unitName,
            String smallName,
            String formationDate,
            String debutDate,
            boolean visible,
            Long parentUnitId,
            List<Long> artistIds,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유닛이 존재하지 않습니다. id=" + unitId));
        applyFields(unit, unitId, unitName, smallName, formationDate, debutDate, visible, parentUnitId, artistIds, image, imageUrl, clearImage);
        return toDto(unitRepository.save(unit));
    }

    @Transactional
    public void delete(long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유닛이 존재하지 않습니다. id=" + unitId));

        // 하위 유닛은 상위만 끊고 남긴다
        if (unit.getSubUnits() != null && !unit.getSubUnits().isEmpty()) {
            for (Unit child : new ArrayList<>(unit.getSubUnits())) {
                child.setParentUnit(null);
            }
            unit.getSubUnits().clear();
        }

        // 앨범은 삭제하지 않고 유닛 연결만 해제
        if (unit.getAlbums() != null && !unit.getAlbums().isEmpty()) {
            for (Album album : new ArrayList<>(unit.getAlbums())) {
                album.setUnit(null);
            }
            unit.getAlbums().clear();
        }

        if (unit.getUnitMembers() != null) {
            unit.getUnitMembers().clear();
        }

        deleteManagedImage(unit.getUnitImage());
        unitRepository.delete(unit);
    }

    private void applyFields(
            Unit unit,
            Long unitId,
            String unitName,
            String smallName,
            String formationDate,
            String debutDate,
            boolean visible,
            Long parentUnitId,
            List<Long> artistIds,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        String name = requireText(unitName, "유닛명은 필수입니다.");
        String small = requireText(smallName, "짧은 이름(smallName)은 필수입니다.").toLowerCase(Locale.ROOT);
        String formation = requireText(formationDate, "결성일은 필수입니다.");
        String debut = debutDate != null ? debutDate.trim() : "";

        unit.setUnitName(name);
        unit.setSmallName(small);
        unit.setFormationDate(formation);
        unit.setDebutDate(debut);
        unit.setVisible(visible);

        if (parentUnitId != null && parentUnitId > 0) {
            if (Objects.equals(parentUnitId, unitId)) {
                throw new IllegalArgumentException("자기 자신을 상위 유닛으로 지정할 수 없습니다.");
            }
            Unit parent = unitRepository.findById(parentUnitId)
                    .orElseThrow(() -> new IllegalArgumentException("상위 유닛이 없습니다. id=" + parentUnitId));
            unit.setParentUnit(parent);
        } else {
            unit.setParentUnit(null);
        }

        if (image != null && !image.isEmpty()) {
            try {
                String previous = unit.getUnitImage();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "unit");
                unit.setUnitImage(meta.getUrl());
                deleteManagedImage(previous);
            } catch (IOException e) {
                throw new IllegalStateException("유닛 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            deleteManagedImage(unit.getUnitImage());
            unit.setUnitImage(null);
        } else if (StringUtils.hasText(imageUrl)) {
            String url = normalizeExternalImageUrl(imageUrl);
            String previous = unit.getUnitImage();
            unit.setUnitImage(url);
            if (!Objects.equals(previous, url)) {
                deleteManagedImage(previous);
            }
        }

        syncMembers(unit, artistIds);
    }

    private static String normalizeExternalImageUrl(String raw) {
        String url = raw.trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("이미지 URL은 http(s)로 시작해야 합니다.");
        }
        if (url.length() > 1000) {
            throw new IllegalArgumentException("이미지 URL이 너무 깁니다.");
        }
        return url;
    }

    private void syncMembers(Unit unit, List<Long> artistIds) {
        Set<Long> wanted = new LinkedHashSet<>();
        if (artistIds != null) {
            for (Long id : artistIds) {
                if (id != null) wanted.add(id);
            }
        }
        if (unit.getUnitMembers() == null) {
            unit.setUnitMembers(new ArrayList<>());
        } else {
            unit.getUnitMembers().clear();
        }
        for (Long artistId : wanted) {
            Artist artist = artistRepository.findById(artistId)
                    .orElseThrow(() -> new IllegalArgumentException("멤버가 없습니다. id=" + artistId));
            UnitMember link = new UnitMember();
            link.setArtist(artist);
            link.setUnit(unit);
            unit.getUnitMembers().add(link);
        }
    }

    private UnitAdminDto toDto(Unit unit) {
        List<Long> artistIds = new ArrayList<>();
        List<UnitAdminDto.MemberOption> members = new ArrayList<>();
        if (unit.getUnitMembers() != null) {
            for (UnitMember um : unit.getUnitMembers()) {
                if (um.getArtist() == null) continue;
                artistIds.add(um.getArtist().getArtistId());
                members.add(UnitAdminDto.MemberOption.builder()
                        .artistId(um.getArtist().getArtistId())
                        .name(um.getArtist().getName())
                        .build());
            }
        }
        Unit parent = unit.getParentUnit();
        return UnitAdminDto.builder()
                .unitId(unit.getUnitId())
                .unitName(unit.getUnitName())
                .smallName(unit.getSmallName())
                .unitImage(unit.getUnitImage())
                .formationDate(unit.getFormationDate())
                .debutDate(unit.getDebutDate())
                .visible(unit.isVisible())
                .parentUnitId(parent != null ? parent.getUnitId() : null)
                .parentUnitName(parent != null ? parent.getUnitName() : null)
                .artistIds(artistIds)
                .members(members)
                .build();
    }

    private void deleteManagedImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return;
        String relative = fileStorageService.toRelativePath(imageUrl);
        if (relative == null || !relative.startsWith("images/unit/")) return;
        try {
            fileStorageService.deleteFile(imageUrl);
        } catch (RuntimeException ignored) {
        }
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

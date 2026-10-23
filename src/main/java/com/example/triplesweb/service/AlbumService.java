package com.example.triplesweb.service;

import com.example.triplesweb.dto.AlbumSectionGroupDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.AlbumSection;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.AlbumSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 앨범 목록·상세 조회. 목록은 구분(섹션)별로 묶고 미배정은 「기타」.
 */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final AlbumSectionRepository albumSectionRepository;

    @Transactional(readOnly = true)
    public List<Album> listAll() {
        return albumRepository.findAll(Sort.by("releaseDate").ascending()
                .and(Sort.by("albumId").ascending()));
    }

    @Transactional(readOnly = true)
    public List<AlbumSectionGroupDto> listGroupedBySection() {
        List<AlbumSection> sections = albumSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc();
        List<Album> albums = albumRepository.findAll(Sort.by("releaseDate").ascending()
                .and(Sort.by("albumId").ascending()));

        Map<Long, AlbumSectionGroupDto> byId = new LinkedHashMap<>();
        for (AlbumSection section : sections) {
            byId.put(section.getSectionId(), AlbumSectionGroupDto.builder()
                    .sectionId(section.getSectionId())
                    .name(section.getName())
                    .other(false)
                    .tabKey("album-section-" + section.getSectionId())
                    .albums(new ArrayList<>())
                    .build());
        }

        List<Album> unassigned = new ArrayList<>();
        for (Album album : albums) {
            AlbumSection section = album.getSection();
            if (section != null && section.getSectionId() != null && byId.containsKey(section.getSectionId())) {
                byId.get(section.getSectionId()).getAlbums().add(album);
            } else {
                unassigned.add(album);
            }
        }

        List<AlbumSectionGroupDto> groups = new ArrayList<>();
        for (AlbumSectionGroupDto group : byId.values()) {
            if (!group.getAlbums().isEmpty()) {
                groups.add(withTabImage(group));
            }
        }
        if (!unassigned.isEmpty()) {
            groups.add(withTabImage(AlbumSectionGroupDto.builder()
                    .sectionId(null)
                    .name("기타")
                    .other(true)
                    .tabKey("album-section-other")
                    .albums(unassigned)
                    .build()));
        }
        // If no sections exist yet, show a single untitled group of all albums as 기타
        // (already covered when all are unassigned).
        // If everything is empty, return empty list.
        if (groups.isEmpty() && !albums.isEmpty()) {
            // all albums somehow skipped — shouldn't happen
            groups.add(withTabImage(AlbumSectionGroupDto.builder()
                    .name("기타")
                    .other(true)
                    .tabKey("album-section-other")
                    .albums(albums)
                    .build()));
        }
        return groups;
    }

    private static AlbumSectionGroupDto withTabImage(AlbumSectionGroupDto group) {
        if (group.getAlbums() != null && !group.getAlbums().isEmpty()) {
            group.setTabImage(group.getAlbums().get(0).getDisplayAlbumImage());
        } else {
            group.setTabImage(com.example.triplesweb.util.ImageUrls.PLACEHOLDER);
        }
        return group;
    }

    @Transactional(readOnly = true)
    public Album getDetail(long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        if (album.getUnit() != null && album.getUnit().getSubUnits() != null) {
            album.getUnit().getSubUnits().size();
        }
        if (album.getTracks() != null) {
            album.getTracks().size();
        }
        if (album.getSection() != null) {
            album.getSection().getName();
        }
        return album;
    }
}

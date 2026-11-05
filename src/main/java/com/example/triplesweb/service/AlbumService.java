package com.example.triplesweb.service;

import com.example.triplesweb.dto.AlbumListCard;
import com.example.triplesweb.dto.AlbumSectionGroupDto;
import com.example.triplesweb.dto.HomeAlbumHero;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.AlbumSection;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.AlbumSectionRepository;
import com.example.triplesweb.util.ImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 앨범 목록·상세 조회. 목록은 구분(섹션)별로 묶고 미배정은 「기타」.
 */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private static final long LIST_CACHE_TTL_MS = 120_000L;
    /** 홈 히어로는 한국·일본 발매만 대상으로 한다. */
    static final List<String> HOME_HERO_SECTIONS = List.of("한국", "일본");

    private final AlbumRepository albumRepository;
    private final AlbumSectionRepository albumSectionRepository;

    private volatile List<AlbumSectionGroupDto> listCache;
    private volatile long listCacheExpiresAt;

    @Transactional(readOnly = true)
    public List<Album> listAll() {
        return albumRepository.findAll(Sort.by("releaseDate").ascending()
                .and(Sort.by("albumId").ascending()));
    }

    @Transactional(readOnly = true)
    public List<AlbumSectionGroupDto> listGroupedBySection() {
        long now = System.currentTimeMillis();
        List<AlbumSectionGroupDto> cached = listCache;
        if (cached != null && now < listCacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (listCache != null && System.currentTimeMillis() < listCacheExpiresAt) {
                return listCache;
            }
            List<AlbumSectionGroupDto> fresh = buildGroupedBySection();
            listCache = fresh;
            listCacheExpiresAt = System.currentTimeMillis() + LIST_CACHE_TTL_MS;
            return fresh;
        }
    }

    /** 관리자 저장·삭제 후 목록 캐시를 바로 비운다. */
    public void invalidateListCache() {
        listCache = null;
        listCacheExpiresAt = 0L;
    }

    private List<AlbumSectionGroupDto> buildGroupedBySection() {
        List<AlbumSection> sections = albumSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc();
        List<AlbumListCard> albums = albumRepository.findAllListCards();

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

        List<AlbumListCard> unassigned = new ArrayList<>();
        for (AlbumListCard album : albums) {
            Long sectionId = album.getSectionId();
            if (sectionId != null && byId.containsKey(sectionId)) {
                byId.get(sectionId).getAlbums().add(album);
            } else {
                unassigned.add(album);
            }
        }

        List<AlbumSectionGroupDto> groups = new ArrayList<>();
        for (AlbumSectionGroupDto group : byId.values()) {
            if (!group.getAlbums().isEmpty()) {
                groups.add(group);
            }
        }
        if (!unassigned.isEmpty()) {
            groups.add(AlbumSectionGroupDto.builder()
                    .sectionId(null)
                    .name("기타")
                    .other(true)
                    .tabKey("album-section-other")
                    .albums(unassigned)
                    .build());
        }
        if (groups.isEmpty() && !albums.isEmpty()) {
            groups.add(AlbumSectionGroupDto.builder()
                    .name("기타")
                    .other(true)
                    .tabKey("album-section-other")
                    .albums(albums)
                    .build());
        }
        return groups;
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
        if (album.getArtists() != null) {
            album.getArtists().size();
        }
        return album;
    }

    /**
     * 홈 히어로: 한·일 중 가장 최신 앨범.
     * 선공개 싱글·커버/소개 부족 앨범은 제외한다.
     */
    @Transactional(readOnly = true)
    public HomeAlbumHero findLatestHomeHero() {
        return albumRepository.findForHomeHero(HOME_HERO_SECTIONS).stream()
                .filter(AlbumService::isHomeHeroEligible)
                .findFirst()
                .map(HomeAlbumHero::from)
                .orElseGet(HomeAlbumHero::fallback);
    }

    static boolean isHomeHeroEligible(Album album) {
        if (album == null) {
            return false;
        }
        if (isPreReleaseSingle(album.getAlbumFormat())) {
            return false;
        }
        if (!hasUsableCover(album.getAlbumImage())) {
            return false;
        }
        return StringUtils.hasText(album.getAlbumInfo());
    }

    static boolean isPreReleaseSingle(String albumFormat) {
        return StringUtils.hasText(albumFormat) && albumFormat.contains("선공개");
    }

    static boolean hasUsableCover(String albumImage) {
        if (!StringUtils.hasText(albumImage)) {
            return false;
        }
        String trimmed = albumImage.trim();
        if (ImageUrls.PLACEHOLDER.equals(trimmed)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        // 임시 SVG·미완성 커버는 히어로에서 제외
        return !lower.endsWith(".svg");
    }
}

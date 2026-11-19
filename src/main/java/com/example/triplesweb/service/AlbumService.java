package com.example.triplesweb.service;

import com.example.triplesweb.dto.AlbumListCard;
import com.example.triplesweb.dto.AlbumSectionGroupDto;
import com.example.triplesweb.dto.ArtistOstAlbumCard;
import com.example.triplesweb.dto.HomeAlbumHero;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.AlbumSection;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.AlbumSectionRepository;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.util.ImageUrls;
import com.example.triplesweb.util.MemberPortraitOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 앨범 목록·상세 조회. 목록은 구분(섹션)별로 묶고 미배정은 「기타」.
 */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private static final long LIST_CACHE_TTL_MS = 120_000L;
    /** 홈 히어로는 한국·일본 발매만 대상으로 한다. */
    static final List<String> HOME_HERO_SECTIONS = List.of("한국", "일본");
    /** 앨범 목록 구분 중 OST 섹션명. */
    static final String OST_SECTION_NAME = "OST";

    private final AlbumRepository albumRepository;
    private final AlbumSectionRepository albumSectionRepository;
    private final ArtistRepository artistRepository;

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
        if (album.getUnit() != null) {
            touchUnitMembers(album.getUnit());
            if (album.getUnit().getSubUnits() != null) {
                for (Unit sub : album.getUnit().getSubUnits()) {
                    touchUnitMembers(sub);
                }
            }
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
     * 가사 파트 색칠용 멤버 목록 {@code [{name, color}, ...]}.
     * 우선순위: 앨범 참여 멤버 → 완전체면 전원 → 유닛(+하위) 멤버.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> lyricsColorMembers(Album album) {
        LinkedHashMap<String, String> byName = new LinkedHashMap<>();
        if (album == null) return List.of();

        if (album.getArtists() != null && !album.getArtists().isEmpty()) {
            album.getArtists().stream()
                    .filter(a -> a != null)
                    .sorted(Comparator.comparing(a -> a.getArtistId() != null ? a.getArtistId() : 0L))
                    .forEach(a -> putArtistColor(byName, a));
            return toNameColorList(byName);
        }

        if (album.isFull10() || album.isFull24()) {
            artistRepository.findAll(Sort.by(Sort.Direction.ASC, "artistId"))
                    .forEach(a -> putArtistColor(byName, a));
            return toNameColorList(byName);
        }

        Unit unit = album.getUnit();
        if (unit != null) {
            addUnitArtistColors(byName, unit);
            if (unit.getSubUnits() != null) {
                for (Unit sub : unit.getSubUnits()) {
                    addUnitArtistColors(byName, sub);
                }
            }
        }
        return toNameColorList(byName);
    }

    private static void touchUnitMembers(Unit unit) {
        if (unit == null || unit.getUnitMembers() == null) return;
        for (UnitMember membership : unit.getUnitMembers()) {
            if (membership != null && membership.getArtist() != null) {
                membership.getArtist().getName();
                membership.getArtist().getColor();
            }
        }
    }

    private static void addUnitArtistColors(Map<String, String> byName, Unit unit) {
        if (unit == null || unit.getUnitMembers() == null) return;
        for (UnitMember membership : unit.getUnitMembers()) {
            if (membership != null) putArtistColor(byName, membership.getArtist());
        }
    }

    private static void putArtistColor(Map<String, String> byName, Artist artist) {
        if (artist == null || !StringUtils.hasText(artist.getName())) return;
        String color = StringUtils.hasText(artist.getColor()) ? artist.getColor().trim() : "#e3e2e8";
        byName.putIfAbsent(artist.getName().trim(), color);
    }

    private static List<Map<String, String>> toNameColorList(Map<String, String> byName) {
        List<Map<String, String>> out = new ArrayList<>(byName.size());
        byName.forEach((name, color) -> out.add(Map.of("name", name, "color", color)));
        return out;
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

    /**
     * 멤버 프로필에 OST 참여를 붙인다.
     * 24인 완전체({@code FULL_24})와 참여 멤버가 전원인 OST는 제외한다.
     */
    @Transactional(readOnly = true)
    public void attachOstAlbums(List<Artist> artists) {
        if (artists == null || artists.isEmpty()) {
            return;
        }
        int fullRoster = MemberPortraitOptions.koreanOrder().size();
        Map<Long, List<ArtistOstAlbumCard>> byArtist = new LinkedHashMap<>();
        List<Album> ostAlbums = new ArrayList<>(albumRepository.findOstAlbumsWithArtists(OST_SECTION_NAME));
        ostAlbums.sort(Comparator
                .comparing(Album::getReleaseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Album::getAlbumId, Comparator.nullsLast(Comparator.reverseOrder())));
        for (Album album : ostAlbums) {
            if (!isPartialOstParticipation(album, fullRoster)) {
                continue;
            }
            ArtistOstAlbumCard card = toOstCard(album);
            Set<Long> seen = new LinkedHashSet<>();
            for (Artist participant : album.getArtists()) {
                if (participant == null || participant.getArtistId() == null) {
                    continue;
                }
                Long artistId = participant.getArtistId();
                if (!seen.add(artistId)) {
                    continue;
                }
                byArtist.computeIfAbsent(artistId, ignored -> new ArrayList<>()).add(card);
            }
        }
        for (Artist artist : artists) {
            if (artist == null || artist.getArtistId() == null) {
                continue;
            }
            artist.setDisplayOstAlbums(List.copyOf(byArtist.getOrDefault(artist.getArtistId(), List.of())));
        }
    }

    static boolean isPartialOstParticipation(Album album, int fullRosterSize) {
        if (album == null || album.getArtists() == null || album.getArtists().isEmpty()) {
            return false;
        }
        return album.getArtists().size() < fullRosterSize;
    }

    private static ArtistOstAlbumCard toOstCard(Album album) {
        return new ArtistOstAlbumCard(
                album.getAlbumId(),
                album.getAlbumName(),
                album.getDisplayAlbumImage());
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

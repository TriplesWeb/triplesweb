package com.example.triplesweb.service;

import com.example.triplesweb.dto.ArtistAdminDto;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.SiteContent;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.SiteContentRepository;
import com.example.triplesweb.repository.UnitRepository;
import com.example.triplesweb.util.ContentLinks;
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
public class ArtistAdminService {

    public static final String KEY_GROUP_TITLE = "about.group.title";
    public static final String KEY_GROUP_BODY = "about.group.body";
    public static final String KEY_GROUP_IMAGE = "about.group.image";
    public static final String KEY_GROUP_TAB_IMAGE = "about.group.tabImage";

    private static final String DEFAULT_GROUP_TITLE = "tripleS (트리플에스)";
    private static final String DEFAULT_GROUP_BODY =
            "tripleS(한국어: 트리플에스)는 모드하우스가 결성한 대한민국의 24인조 걸그룹이다. "
                    + "2022년 5월 시작된 이래 정식 데뷔 9개월 이전의 프로젝트를 통하여 대중에게 소개되었다. "
                    + "국내 최다인원 24명으로 이루어진 걸그룹으로 ASSEMBLE,ASSEMBLE24,ASSEMBLE25 등을 발매하였다.";
    private static final String DEFAULT_GROUP_IMAGE =
            "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w4sFOxa0qwo4UedO_C0_jcoBe03789wNc5YmISVsRpQCUGa0obgJJFcjBxDI9La7P22k1Y69v81a-3yCmFW4bIEncacdQLXxQ8oCq1Bu4BaK72Rqff_oF6s-6VOylFoxz4vRAjLTVYAD_Y2d1Q3cMkQ.webp";

    private final ArtistRepository artistRepository;
    private final UnitRepository unitRepository;
    private final SiteContentRepository siteContentRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public GroupIntroDto getGroupIntro() {
        String imageUrl = getContent(KEY_GROUP_IMAGE, DEFAULT_GROUP_IMAGE);
        return GroupIntroDto.builder()
                .title(getContent(KEY_GROUP_TITLE, DEFAULT_GROUP_TITLE).replaceAll("(?i)\\btriples\\b", "tripleS"))
                .body(getContent(KEY_GROUP_BODY, DEFAULT_GROUP_BODY))
                .imageUrl(imageUrl)
                // 탭 썸네일도 대표 이미지와 동일하게 노출
                .tabImageUrl(imageUrl)
                .build();
    }

    @Transactional
    public GroupIntroDto updateGroupIntro(
            String title,
            String body,
            MultipartFile image,
            boolean clearImage
    ) {
        putContent(KEY_GROUP_TITLE, StringUtils.hasText(title) ? title.trim() : DEFAULT_GROUP_TITLE);
        putContent(KEY_GROUP_BODY, body != null ? body.trim() : "");

        // 소개 본문 이미지와 상단 탭 썸네일은 동일 이미지로 유지한다.
        if (image != null && !image.isEmpty()) {
            try {
                String previousImage = getContent(KEY_GROUP_IMAGE, null);
                String previousTab = getContent(KEY_GROUP_TAB_IMAGE, null);
                SavedMeta meta = fileStorageService.saveWithMeta(image, "artist");
                putContent(KEY_GROUP_IMAGE, meta.getUrl());
                putContent(KEY_GROUP_TAB_IMAGE, meta.getUrl());
                deleteManagedArtistImage(previousImage);
                if (!Objects.equals(previousTab, previousImage)) {
                    deleteManagedArtistImage(previousTab);
                }
            } catch (IOException e) {
                throw new IllegalStateException("그룹 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            String previousImage = getContent(KEY_GROUP_IMAGE, null);
            String previousTab = getContent(KEY_GROUP_TAB_IMAGE, null);
            deleteManagedArtistImage(previousImage);
            if (!Objects.equals(previousTab, previousImage)) {
                deleteManagedArtistImage(previousTab);
            }
            putContent(KEY_GROUP_IMAGE, DEFAULT_GROUP_IMAGE);
            putContent(KEY_GROUP_TAB_IMAGE, DEFAULT_GROUP_IMAGE);
        }

        return getGroupIntro();
    }

    @Transactional(readOnly = true)
    public ArtistAdminDto getArtist(long artistId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("해당 아티스트가 존재하지 않습니다. id=" + artistId));
        // units may be lazy
        artist.getAffiliatedUnit().size();
        return toDto(artist);
    }

    @Transactional
    public ArtistAdminDto updateArtist(
            long artistId,
            String name,
            String engName,
            String nationality,
            String birthDate,
            String bornPlace,
            String birthPlace,
            String realName,
            String mbti,
            String debutDate,
            String color,
            String soloContent,
            String broadcastContent,
            List<Long> unitIds,
            MultipartFile image,
            boolean clearImage,
            MultipartFile signatureImage,
            boolean clearSignatureImage
    ) {
        Artist artist = artistRepository.findByIdWithAffiliatedUnits(artistId)
                .orElseThrow(() -> new IllegalArgumentException("해당 아티스트가 존재하지 않습니다. id=" + artistId));

        String trimmedName = requireText(name, "이름은 필수입니다.");
        String trimmedEng = requireText(engName, "영문명은 필수입니다.");
        String trimmedNation = requireText(nationality, "국적은 필수입니다.");
        String trimmedBirthDate = requireText(birthDate, "출생은 필수입니다.");
        String trimmedBornPlace = bornPlace != null ? bornPlace.trim() : "";
        String trimmedBirthPlace = birthPlace != null ? birthPlace.trim() : "";
        String trimmedRealName = realName != null ? realName.trim() : "";
        String trimmedMbti = normalizeMbti(mbti);
        String trimmedDebut = requireText(debutDate, "데뷔일은 필수입니다.");
        String trimmedColor = normalizeColor(color);

        artistRepository.findByName(trimmedName).ifPresent(other -> {
            if (!Objects.equals(other.getArtistId(), artistId)) {
                throw new IllegalArgumentException("이미 사용 중인 이름입니다: " + trimmedName);
            }
        });
        artistRepository.findByEngNameIgnoreCase(trimmedEng).ifPresent(other -> {
            if (!Objects.equals(other.getArtistId(), artistId)) {
                throw new IllegalArgumentException("이미 사용 중인 영문명입니다: " + trimmedEng);
            }
        });

        artist.setName(trimmedName);
        artist.setEngName(trimmedEng);
        artist.setNationality(trimmedNation);
        artist.setBirthDate(trimmedBirthDate);
        artist.setBornPlace(trimmedBornPlace.isEmpty() ? null : trimmedBornPlace);
        artist.setBirthPlace(trimmedBirthPlace.isEmpty() ? null : trimmedBirthPlace);
        artist.setRealName(trimmedRealName.isEmpty() ? null : trimmedRealName);
        artist.setMbti(trimmedMbti);
        artist.setDebutDate(trimmedDebut);
        artist.setColor(trimmedColor);
        artist.setSoloContent(ContentLinks.normalizeJson(soloContent));
        artist.setBroadcastContent(ContentLinks.normalizeJson(broadcastContent));

        if (image != null && !image.isEmpty()) {
            try {
                String previous = artist.getProfileImage();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "artist");
                artist.setProfileImage(meta.getUrl());
                deleteManagedArtistImage(previous);
            } catch (IOException e) {
                throw new IllegalStateException("프로필 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            deleteManagedArtistImage(artist.getProfileImage());
            artist.setProfileImage(null);
        }

        if (signatureImage != null && !signatureImage.isEmpty()) {
            try {
                String previous = artist.getSignatureImage();
                SavedMeta meta = fileStorageService.saveWithMeta(signatureImage, "artist");
                artist.setSignatureImage(meta.getUrl());
                deleteManagedArtistImage(previous);
            } catch (IOException e) {
                throw new IllegalStateException("싸인 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearSignatureImage) {
            deleteManagedArtistImage(artist.getSignatureImage());
            artist.setSignatureImage(null);
        }

        syncUnits(artist, unitIds);
        return toDto(artistRepository.save(artist));
    }

    private void syncUnits(Artist artist, List<Long> unitIds) {
        Set<Long> wanted = new LinkedHashSet<>();
        if (unitIds != null) {
            for (Long id : unitIds) {
                if (id != null) wanted.add(id);
            }
        }
        if (artist.getAffiliatedUnit() == null) {
            artist.setAffiliatedUnit(new ArrayList<>());
        }
        // Keep persisted links for retained units: recreating them can INSERT before
        // orphan removal DELETEs and violate uk_unit_member (artist_id, unit_id).
        artist.getAffiliatedUnit().removeIf(link -> link.getUnit() == null
                || !wanted.contains(link.getUnit().getUnitId()));
        Set<Long> existing = artist.getAffiliatedUnit().stream()
                .map(link -> link.getUnit().getUnitId())
                .collect(Collectors.toSet());
        for (Long unitId : wanted) {
            if (existing.contains(unitId)) continue;
            Unit unit = unitRepository.findById(unitId)
                    .orElseThrow(() -> new IllegalArgumentException("유닛이 없습니다. id=" + unitId));
            UnitMember link = new UnitMember();
            link.setArtist(artist);
            link.setUnit(unit);
            artist.getAffiliatedUnit().add(link);
            existing.add(unitId);
        }
    }

    private ArtistAdminDto toDto(Artist artist) {
        List<ArtistAdminDto.UnitOption> units = new ArrayList<>();
        List<Long> unitIds = new ArrayList<>();
        if (artist.getAffiliatedUnit() != null) {
            for (UnitMember um : artist.getAffiliatedUnit()) {
                if (um.getUnit() == null) continue;
                unitIds.add(um.getUnit().getUnitId());
                units.add(ArtistAdminDto.UnitOption.builder()
                        .unitId(um.getUnit().getUnitId())
                        .unitName(um.getUnit().getUnitName())
                        .build());
            }
        }
        return ArtistAdminDto.builder()
                .artistId(artist.getArtistId())
                .name(artist.getName())
                .engName(artist.getEngName())
                .nationality(artist.getNationality())
                .birthDate(artist.getBirthDate())
                .bornPlace(artist.getBornPlace())
                .birthPlace(artist.getBirthPlace())
                .realName(artist.getRealName())
                .mbti(artist.getMbti())
                .debutDate(artist.getDebutDate())
                .color(artist.getColor())
                .profileImage(artist.getProfileImage())
                .signatureImage(artist.getSignatureImage())
                .soloLinks(artist.getSoloLinks())
                .broadcastLinks(artist.getBroadcastLinks())
                .unitIds(unitIds)
                .units(units)
                .build();
    }

    public List<ArtistAdminDto.UnitOption> listUnitOptions() {
        return unitRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(
                        a.getUnitId() != null ? a.getUnitId() : 0L,
                        b.getUnitId() != null ? b.getUnitId() : 0L))
                .map(u -> ArtistAdminDto.UnitOption.builder()
                        .unitId(u.getUnitId())
                        .unitName(u.getUnitName())
                        .build())
                .collect(Collectors.toList());
    }

    private String getContent(String key, String defaultValue) {
        return siteContentRepository.findById(key)
                .map(SiteContent::getContentValue)
                .filter(StringUtils::hasText)
                .orElse(defaultValue);
    }

    private void putContent(String key, String value) {
        SiteContent row = siteContentRepository.findById(key).orElseGet(SiteContent::new);
        row.setContentKey(key);
        row.setContentValue(value != null ? value : "");
        siteContentRepository.save(row);
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeColor(String color) {
        String raw = requireText(color, "상징 색은 필수입니다.");
        if (!raw.startsWith("#")) raw = "#" + raw;
        if (!raw.matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("상징 색은 #RRGGBB 형식이어야 합니다.");
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /** 비우면 null. 있으면 대문자·길이 제한만 적용 (예: INFP). */
    private static String normalizeMbti(String mbti) {
        if (!StringUtils.hasText(mbti)) return null;
        String trimmed = mbti.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() > 16) {
            throw new IllegalArgumentException("MBTI는 16자 이하여야 합니다.");
        }
        return trimmed;
    }

    private void deleteManagedArtistImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return;
        String relative = fileStorageService.toRelativePath(imageUrl);
        if (relative == null || !relative.startsWith("images/artist/")) return;
        try {
            fileStorageService.deleteFile(imageUrl);
        } catch (RuntimeException ignored) {
        }
    }
}

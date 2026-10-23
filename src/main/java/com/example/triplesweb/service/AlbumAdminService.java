package com.example.triplesweb.service;

import com.example.triplesweb.dto.AlbumAdminDto;
import com.example.triplesweb.dto.AlbumSectionDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.AlbumSection;
import com.example.triplesweb.entity.AlbumType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Track;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.AlbumSectionRepository;
import com.example.triplesweb.repository.UnitRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlbumAdminService {

    private final AlbumRepository albumRepository;
    private final AlbumSectionRepository albumSectionRepository;
    private final UnitRepository unitRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AlbumAdminDto get(long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        if (album.getTracks() != null) album.getTracks().size();
        if (album.getUnit() != null) album.getUnit().getUnitName();
        if (album.getSection() != null) album.getSection().getName();
        return toDto(album);
    }

    @Transactional(readOnly = true)
    public List<AlbumAdminDto.UnitOption> listUnitOptions() {
        return unitRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(
                        a.getUnitId() != null ? a.getUnitId() : 0L,
                        b.getUnitId() != null ? b.getUnitId() : 0L))
                .map(u -> AlbumAdminDto.UnitOption.builder()
                        .unitId(u.getUnitId())
                        .unitName(u.getUnitName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AlbumSectionDto> listSections() {
        return albumSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(this::toSectionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AlbumSectionDto createSection(String name) {
        String trimmed = requireText(name, "구분명은 필수입니다.");
        albumSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
            throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
        });
        int nextOrder = albumSectionRepository.findAll().stream()
                .mapToInt(AlbumSection::getSortOrder)
                .max()
                .orElse(-1) + 1;
        AlbumSection section = new AlbumSection(null, trimmed, nextOrder);
        return toSectionDto(albumSectionRepository.save(section));
    }

    @Transactional
    public AlbumSectionDto updateSection(long sectionId, String name, Integer sortOrder) {
        AlbumSection section = albumSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            albumSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
                if (!Objects.equals(other.getSectionId(), sectionId)) {
                    throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
                }
            });
            section.setName(trimmed);
        }
        if (sortOrder != null) {
            section.setSortOrder(sortOrder);
        }
        return toSectionDto(albumSectionRepository.save(section));
    }

    @Transactional
    public void deleteSection(long sectionId) {
        AlbumSection section = albumSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        for (Album album : albumRepository.findAll()) {
            if (album.getSection() != null && Objects.equals(album.getSection().getSectionId(), sectionId)) {
                album.setSection(null);
            }
        }
        albumSectionRepository.delete(section);
    }

    @Transactional
    public List<AlbumSectionDto> moveSection(long sectionId, int direction) {
        List<AlbumSection> sections = new ArrayList<>(
                albumSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc());
        int index = -1;
        for (int i = 0; i < sections.size(); i++) {
            if (Objects.equals(sections.get(i).getSectionId(), sectionId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId);
        }
        int swapWith = index + (direction < 0 ? -1 : 1);
        if (swapWith < 0 || swapWith >= sections.size()) {
            return sections.stream().map(this::toSectionDto).collect(Collectors.toList());
        }
        AlbumSection a = sections.get(index);
        AlbumSection b = sections.get(swapWith);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        // keep stable if equal orders
        if (a.getSortOrder() == b.getSortOrder()) {
            a.setSortOrder(swapWith);
            b.setSortOrder(index);
        }
        albumSectionRepository.save(a);
        albumSectionRepository.save(b);
        return albumSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(this::toSectionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AlbumAdminDto create(
            String albumName,
            String releaseDate,
            String albumInfo,
            String albumType,
            String albumFormat,
            Long unitId,
            Long sectionId,
            String tracksJson,
            MultipartFile image,
            String imageUrl
    ) {
        Album album = new Album();
        applyFields(album, null, albumName, releaseDate, albumInfo, albumType, albumFormat, unitId, sectionId,
                tracksJson, image, imageUrl, false);
        return toDto(albumRepository.save(album));
    }

    @Transactional
    public AlbumAdminDto update(
            long albumId,
            String albumName,
            String releaseDate,
            String albumInfo,
            String albumType,
            String albumFormat,
            Long unitId,
            Long sectionId,
            String tracksJson,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        applyFields(album, albumId, albumName, releaseDate, albumInfo, albumType, albumFormat, unitId, sectionId,
                tracksJson, image, imageUrl, clearImage);
        return toDto(albumRepository.save(album));
    }

    @Transactional
    public void delete(long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        deleteManagedImage(album.getAlbumImage());
        albumRepository.delete(album);
    }

    private void applyFields(
            Album album,
            Long albumId,
            String albumName,
            String releaseDate,
            String albumInfo,
            String albumType,
            String albumFormat,
            Long unitId,
            Long sectionId,
            String tracksJson,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        String name = requireText(albumName, "앨범명은 필수입니다.");
        String date = requireText(releaseDate, "발매일은 필수입니다.");
        String info = albumInfo != null ? albumInfo.trim() : "";

        albumRepository.findByAlbumName(name).ifPresent(other -> {
            if (!Objects.equals(other.getAlbumId(), albumId)) {
                throw new IllegalArgumentException("이미 사용 중인 앨범명입니다: " + name);
            }
        });

        album.setAlbumName(name);
        album.setReleaseDate(date);
        album.setAlbumInfo(info);
        album.setAlbumType(parseAlbumType(albumType));
        album.setAlbumFormat(normalizeAlbumFormat(albumFormat));

        if (unitId != null && unitId > 0) {
            Unit unit = unitRepository.findById(unitId)
                    .orElseThrow(() -> new IllegalArgumentException("유닛이 없습니다. id=" + unitId));
            album.setUnit(unit);
        } else {
            album.setUnit(null);
        }

        if (sectionId != null && sectionId > 0) {
            AlbumSection section = albumSectionRepository.findById(sectionId)
                    .orElseThrow(() -> new IllegalArgumentException("구분이 없습니다. id=" + sectionId));
            album.setSection(section);
        } else {
            album.setSection(null);
        }

        if (image != null && !image.isEmpty()) {
            try {
                String previous = album.getAlbumImage();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "album");
                album.setAlbumImage(meta.getUrl());
                deleteManagedImage(previous);
            } catch (IOException e) {
                throw new IllegalStateException("앨범 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            deleteManagedImage(album.getAlbumImage());
            album.setAlbumImage(null);
        } else if (StringUtils.hasText(imageUrl)) {
            String url = normalizeExternalImageUrl(imageUrl);
            String previous = album.getAlbumImage();
            album.setAlbumImage(url);
            if (!Objects.equals(previous, url)) {
                deleteManagedImage(previous);
            }
        }

        replaceTracks(album, parseTracks(tracksJson));
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

    private void replaceTracks(Album album, List<AlbumAdminDto.TrackItem> items) {
        if (album.getTracks() == null) {
            album.setTracks(new ArrayList<>());
        } else {
            album.getTracks().clear();
        }
        int i = 1;
        for (AlbumAdminDto.TrackItem item : items) {
            if (item == null || !StringUtils.hasText(item.getTitle())) continue;
            Track track = new Track();
            track.setAlbum(album);
            track.setTrackNum(item.getTrackNum() != null && item.getTrackNum() > 0 ? item.getTrackNum() : i);
            track.setTitle(item.getTitle().trim());
            track.setDuration(StringUtils.hasText(item.getDuration()) ? item.getDuration().trim() : null);
            track.setIsTitle(Boolean.TRUE.equals(item.getIsTitle()));
            album.getTracks().add(track);
            i++;
        }
    }

    private List<AlbumAdminDto.TrackItem> parseTracks(String tracksJson) {
        if (!StringUtils.hasText(tracksJson)) return List.of();
        try {
            return objectMapper.readValue(tracksJson, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("트랙 목록 JSON이 올바르지 않습니다.");
        }
    }

    private AlbumType parseAlbumType(String raw) {
        if (!StringUtils.hasText(raw)) return AlbumType.SUBUNIT;
        try {
            return AlbumType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("앨범 유형이 올바르지 않습니다: " + raw);
        }
    }

    private String normalizeAlbumFormat(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String trimmed = raw.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("앨범 규격은 120자 이하여야 합니다.");
        }
        return trimmed;
    }

    private AlbumAdminDto toDto(Album album) {
        List<AlbumAdminDto.TrackItem> tracks = new ArrayList<>();
        if (album.getTracks() != null) {
            for (Track t : album.getTracks()) {
                tracks.add(AlbumAdminDto.TrackItem.builder()
                        .trackNum(t.getTrackNum())
                        .title(t.getTitle())
                        .duration(t.getDuration())
                        .isTitle(Boolean.TRUE.equals(t.getIsTitle()))
                        .build());
            }
        }
        Unit unit = album.getUnit();
        AlbumSection section = album.getSection();
        return AlbumAdminDto.builder()
                .albumId(album.getAlbumId())
                .albumName(album.getAlbumName())
                .albumImage(album.getAlbumImage())
                .releaseDate(album.getReleaseDate())
                .albumInfo(album.getAlbumInfo())
                .albumType(album.getAlbumType() != null ? album.getAlbumType().name() : AlbumType.SUBUNIT.name())
                .albumFormat(album.getAlbumFormat())
                .unitId(unit != null ? unit.getUnitId() : null)
                .unitName(unit != null ? unit.getUnitName() : null)
                .sectionId(section != null ? section.getSectionId() : null)
                .sectionName(section != null ? section.getName() : null)
                .tracks(tracks)
                .build();
    }

    private AlbumSectionDto toSectionDto(AlbumSection section) {
        return AlbumSectionDto.builder()
                .sectionId(section.getSectionId())
                .name(section.getName())
                .sortOrder(section.getSortOrder())
                .build();
    }

    private void deleteManagedImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return;
        String relative = fileStorageService.toRelativePath(imageUrl);
        if (relative == null || !relative.startsWith("images/album/")) return;
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

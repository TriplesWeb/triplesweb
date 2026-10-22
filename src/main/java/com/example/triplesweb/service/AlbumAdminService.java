package com.example.triplesweb.service;

import com.example.triplesweb.dto.AlbumAdminDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.AlbumType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Track;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.repository.AlbumRepository;
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
    private final UnitRepository unitRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AlbumAdminDto get(long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        if (album.getTracks() != null) album.getTracks().size();
        if (album.getUnit() != null) album.getUnit().getUnitName();
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

    @Transactional
    public AlbumAdminDto create(
            String albumName,
            String releaseDate,
            String albumInfo,
            String albumType,
            Long unitId,
            String tracksJson,
            MultipartFile image,
            String imageUrl
    ) {
        Album album = new Album();
        applyFields(album, null, albumName, releaseDate, albumInfo, albumType, unitId, tracksJson, image, imageUrl, false);
        return toDto(albumRepository.save(album));
    }

    @Transactional
    public AlbumAdminDto update(
            long albumId,
            String albumName,
            String releaseDate,
            String albumInfo,
            String albumType,
            Long unitId,
            String tracksJson,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        applyFields(album, albumId, albumName, releaseDate, albumInfo, albumType, unitId, tracksJson, image, imageUrl, clearImage);
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
            Long unitId,
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

        if (unitId != null && unitId > 0) {
            Unit unit = unitRepository.findById(unitId)
                    .orElseThrow(() -> new IllegalArgumentException("유닛이 없습니다. id=" + unitId));
            album.setUnit(unit);
        } else {
            album.setUnit(null);
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
        return AlbumAdminDto.builder()
                .albumId(album.getAlbumId())
                .albumName(album.getAlbumName())
                .albumImage(album.getAlbumImage())
                .releaseDate(album.getReleaseDate())
                .albumInfo(album.getAlbumInfo())
                .albumType(album.getAlbumType() != null ? album.getAlbumType().name() : AlbumType.SUBUNIT.name())
                .unitId(unit != null ? unit.getUnitId() : null)
                .unitName(unit != null ? unit.getUnitName() : null)
                .tracks(tracks)
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

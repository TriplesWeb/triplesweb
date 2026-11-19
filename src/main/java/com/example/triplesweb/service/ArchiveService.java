package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.util.ArchiveFolderClassifier;
import com.example.triplesweb.util.ArchiveImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 아카이브({@link Archive}) 미디어 목록·상세·업로드·삭제를 담당한다.
 * {@link MediaType}별 페이징·키워드 검색, 이미지/영상 전용 업로드 검증을 수행한다.
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchiveRepository archiveRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Archive> index() {
        return archiveRepository.findAll();
    }

    /** 날짜가 확인된 검색 결과를 먼저 최신순으로 반환한다. */
    @Transactional(readOnly = true)
    public List<Archive> searchLatestByType(MediaType type, String keyword, int limit) {
        if (type == null || keyword == null || keyword.isBlank() || limit <= 0) return List.of();
        return archiveRepository.searchLatestByType(type, keyword.trim(), PageRequest.of(0, Math.min(limit, 100)));
    }

    @Transactional(readOnly = true)
    public Page<Archive> pageByType(MediaType type, int page, int size, String keyword) {
        return pageByType(type, page, size, keyword, List.of());
    }

    public Page<Archive> pageByType(MediaType type, int page, int size, String keyword, List<String> members) {
        return pageByType(type, page, size, keyword, members, null, null);
    }

    public Page<Archive> pageByType(MediaType type, int page, int size, String keyword, List<String> members,
                                    LocalDate from, LocalDate to) {
        return pageByType(type, page, size, keyword, members, from, to, null, false);
    }

    @Transactional(readOnly = true)
    public Page<Archive> pageByType(MediaType type, int page, int size, String keyword, List<String> members,
                                    LocalDate from, LocalDate to, ArchiveSource source, boolean unclassifiedOnly) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "id"));
        return archiveRepository.findAll(archiveSpec(type, keyword, members, from, to, source, unclassifiedOnly), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Archive> pageInFolder(MediaType type, int page, int size, String keyword, List<String> members,
                                      LocalDate from, LocalDate to, ArchiveSource source, boolean unclassifiedOnly,
                                      String folder, List<String> comboMembers,
                                      List<ArchiveFolderClassifier.UnitMembers> units) {
        List<Archive> found = archiveRepository.findAll(
                archiveSpec(type, keyword, members, from, to, source, unclassifiedOnly),
                Sort.by(Sort.Direction.DESC, "id"));
        List<Archive> filtered = found.stream()
                .filter(archive -> ArchiveFolderClassifier.matches(
                        ArchiveFolderClassifier.classify(archive.getArtistNames(), units), folder, comboMembers))
                .toList();
        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        int fromIndex = safePage * safeSize;
        Pageable pageable = PageRequest.of(safePage, safeSize);
        if (fromIndex >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }
        return new PageImpl<>(filtered.subList(fromIndex, Math.min(filtered.size(), fromIndex + safeSize)),
                pageable, filtered.size());
    }

    private static Specification<Archive> archiveSpec(
            MediaType type, String keyword, List<String> members, LocalDate from, LocalDate to,
            ArchiveSource source, boolean unclassifiedOnly) {
        String q = keyword == null ? "" : keyword.trim();
        List<String> names = members == null ? List.of() : members.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT).replace(" ", ""))
                .distinct()
                .toList();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("file").get("mediaType"), type));
            if (!q.isEmpty()) {
                String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)));
            }
            if (!names.isEmpty()) {
                Expression<String> normalized = cb.lower(cb.function(
                        "replace", String.class, cb.coalesce(root.get("artistNames"), ""), cb.literal(" "), cb.literal("")));
                Expression<String> wrapped = cb.concat(",", cb.concat(normalized, ","));
                List<Predicate> anyMember = new ArrayList<>();
                for (String name : names) {
                    anyMember.add(cb.like(wrapped, "%," + name + ",%"));
                }
                predicates.add(cb.or(anyMember.toArray(Predicate[]::new)));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("registeredOn"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("registeredOn"), to));
            if (unclassifiedOnly) {
                predicates.add(cb.isNull(root.get("source")));
            } else if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Archive show(Long id) {
        return archiveRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아카이브: " + id));
    }

    @Transactional
    public Archive save(MultipartFile file, String title, MediaType requestedType) throws IOException {
        return save(file, title, requestedType, "");
    }

    @Transactional
    public Archive save(MultipartFile file, String title, MediaType requestedType, String artistNames) throws IOException {
        return save(file, title, requestedType, artistNames, null);
    }

    @Transactional
    public Archive save(MultipartFile file, String title, MediaType requestedType, String artistNames, LocalDate registeredOn) throws IOException {
        return save(file, title, requestedType, artistNames, registeredOn, null);
    }

    @Transactional
    public Archive save(MultipartFile file, String title, MediaType requestedType, String artistNames, LocalDate registeredOn,
                        ArchiveSource source) throws IOException {
        return save(file, title, requestedType, artistNames, registeredOn, source, null);
    }

    @Transactional
    public Archive save(MultipartFile file, String title, MediaType requestedType, String artistNames, LocalDate registeredOn,
                        ArchiveSource source, String description) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 할 파일이 없습니다.");
        }
        MediaType resolved = requestedType == null ? MediaType.OTHER : requestedType;
        String safeTitle = (title == null) ? "" : title.trim();
        if (safeTitle.isEmpty() && resolved == MediaType.VIDEO) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (resolved == MediaType.IMAGE) {
            ensureUniqueImagePost(registeredOn, source, description);
        }
        SavedMeta meta = fileStorageService.saveWithMeta(file);   // SavedMeta 채워서 반환
        coerceType(requestedType, meta);
        Archive archive = new Archive(safeTitle, meta);
        archive.setDescription(normalizeDescription(description));
        archive.setArtistNames(artistNames == null ? "" : artistNames.trim());
        if (registeredOn != null) archive.setRegisteredOn(registeredOn);
        archive.setSource(source);
        if (resolved == MediaType.IMAGE) {
            applyImageUrls(archive, List.of(meta.getUrl()));
        }
        return archiveRepository.save(archive);
    }

    /**
     * 여러 사진을 한 게시글로 저장한다. 첫 장이 표지({@code Archive.getFile()})가 된다.
     */
    @Transactional
    public Archive uploadImages(List<MultipartFile> files, String title, String artistNames, LocalDate registeredOn,
                                ArchiveSource source, String description) throws IOException {
        List<MultipartFile> uploads = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (uploads.isEmpty()) {
            throw new IllegalArgumentException("업로드 할 파일이 없습니다.");
        }
        if (uploads.size() > ArchiveImages.MAX_IMAGES) {
            throw new IllegalArgumentException("사진은 최대 " + ArchiveImages.MAX_IMAGES + "장까지 등록할 수 있습니다.");
        }
        ensureUniqueImagePost(registeredOn, source, description);
        String safeTitle = title == null ? "" : title.trim();
        List<SavedMeta> metas = new ArrayList<>(uploads.size());
        List<String> urls = new ArrayList<>(uploads.size());
        for (MultipartFile file : uploads) {
            SavedMeta meta = fileStorageService.saveWithMeta(file);
            coerceType(MediaType.IMAGE, meta);
            metas.add(meta);
            urls.add(meta.getUrl());
        }
        Archive archive = new Archive(safeTitle, metas.get(0));
        archive.setDescription(normalizeDescription(description));
        archive.setArtistNames(artistNames == null ? "" : artistNames.trim());
        if (registeredOn != null) archive.setRegisteredOn(registeredOn);
        archive.setSource(source);
        applyImageUrls(archive, urls);
        return archiveRepository.save(archive);
    }
    @Transactional
    public Archive upload(MultipartFile file, String title) throws IOException {
        return save(file, title, null);
    }
    @Transactional
    public Archive uploadImageOnly(MultipartFile file, String title,MediaType type) throws IOException {
        return uploadImageOnly(file, title, type, "");
    }

    @Transactional
    public Archive uploadImageOnly(MultipartFile file, String title,MediaType type, String artistNames) throws IOException {
        if (type != null && type != MediaType.IMAGE) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
        return save(file, title, MediaType.IMAGE, artistNames);
    }

    @Transactional
    public Archive uploadImageOnly(MultipartFile file, String title, MediaType type, String artistNames, LocalDate registeredOn) throws IOException {
        return uploadImageOnly(file, title, type, artistNames, registeredOn, null);
    }

    @Transactional
    public Archive uploadImageOnly(MultipartFile file, String title, MediaType type, String artistNames, LocalDate registeredOn,
                                   ArchiveSource source) throws IOException {
        return uploadImageOnly(file, title, type, artistNames, registeredOn, source, null);
    }

    @Transactional
    public Archive uploadImageOnly(MultipartFile file, String title, MediaType type, String artistNames, LocalDate registeredOn,
                                   ArchiveSource source, String description) throws IOException {
        if (type != null && type != MediaType.IMAGE) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
        return save(file, title, MediaType.IMAGE, artistNames, registeredOn, source, description);
    }
    @Transactional
    public Archive uploadVideoOnly(MultipartFile file, String title, MediaType type) throws IOException {
        return uploadVideoOnly(file, title, type, "");
    }
    @Transactional
    public Archive uploadVideoOnly(MultipartFile file, String title, MediaType type, String artistNames) throws IOException {
        return uploadVideoOnly(file, title, type, artistNames, null);
    }

    @Transactional
    public Archive uploadVideoOnly(MultipartFile file, String title, MediaType type, String artistNames, MultipartFile thumbnail) throws IOException {
        if (type != null && type != MediaType.VIDEO) {
            throw new IllegalArgumentException("비디오 파일만 업로드 가능합니다.");
        }
        validateThumbnail(thumbnail);
        Archive archive = save(file, title, MediaType.VIDEO, artistNames);
        return finishVideo(archive, thumbnail);
    }

    @Transactional
    public Archive uploadVideoOnly(MultipartFile file, String title, MediaType type, String artistNames, MultipartFile thumbnail, LocalDate registeredOn) throws IOException {
        return uploadVideoOnly(file, title, type, artistNames, thumbnail, registeredOn, null);
    }

    @Transactional
    public Archive uploadVideoOnly(MultipartFile file, String title, MediaType type, String artistNames, MultipartFile thumbnail,
                                   LocalDate registeredOn, ArchiveSource source) throws IOException {
        if (type != null && type != MediaType.VIDEO) {
            throw new IllegalArgumentException("비디오 파일만 업로드 가능합니다.");
        }
        validateThumbnail(thumbnail);
        Archive archive = save(file, title, MediaType.VIDEO, artistNames, registeredOn, source);
        return finishVideo(archive, thumbnail);
    }

    private Archive finishVideo(Archive archive, MultipartFile thumbnail) throws IOException {
        if (thumbnail != null && !thumbnail.isEmpty()) {
            archive.setThumbnailUrl(fileStorageService.saveWithMeta(thumbnail).getUrl());
        }
        return archiveRepository.save(archive);
    }
    @Transactional
    public boolean delete(long id) {
        return archiveRepository.findById(id).map(archive -> {
            deleteImageSet(archive);
            deleteThumbnail(archive.getThumbnailUrl());
            archiveRepository.delete(archive);
            return true;
        }).orElse(false);
    }

    /**
     * 제목과 출연 표기를 바꾸고, 새 파일이 있으면 기존 미디어를 교체한다.
     * 파일은 비워 둘 수 있으며, 이때 저장된 미디어는 그대로 둔다.
     */
    @Transactional
    public Archive update(long id, String title, MultipartFile file, String artistNames) throws IOException {
        return update(id, title, file, artistNames, null, false);
    }

    @Transactional
    public Archive update(long id, String title, MultipartFile file, String artistNames, MultipartFile thumbnail, boolean clearThumbnail) throws IOException {
        return update(id, title, file, artistNames, thumbnail, clearThumbnail, null);
    }

    @Transactional
    public Archive update(long id, String title, MultipartFile file, String artistNames, MultipartFile thumbnail, boolean clearThumbnail, LocalDate registeredOn) throws IOException {
        return update(id, title, file, artistNames, thumbnail, clearThumbnail, registeredOn, null, false);
    }

    @Transactional
    public Archive update(long id, String title, MultipartFile file, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                          LocalDate registeredOn, ArchiveSource source) throws IOException {
        return update(id, title, file, artistNames, thumbnail, clearThumbnail, registeredOn, source, null);
    }

    @Transactional
    public Archive update(long id, String title, MultipartFile file, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                          LocalDate registeredOn, ArchiveSource source, String description) throws IOException {
        List<MultipartFile> files = file == null || file.isEmpty() ? List.of() : List.of(file);
        return update(id, title, files, artistNames, thumbnail, clearThumbnail, registeredOn, source, true, description, null, null);
    }

    @Transactional
    public Archive update(long id, String title, List<MultipartFile> files, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                          LocalDate registeredOn, ArchiveSource source, String description) throws IOException {
        return update(id, title, files, artistNames, thumbnail, clearThumbnail, registeredOn, source, true, description, null, null);
    }

    @Transactional
    public Archive update(long id, String title, List<MultipartFile> files, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                          LocalDate registeredOn, ArchiveSource source, String description, String keepImageUrlsJson) throws IOException {
        return update(id, title, files, artistNames, thumbnail, clearThumbnail, registeredOn, source, true, description, keepImageUrlsJson, null);
    }

    @Transactional
    public Archive update(long id, String title, List<MultipartFile> files, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                          LocalDate registeredOn, ArchiveSource source, String description, String keepImageUrlsJson,
                          String imageOrderJson) throws IOException {
        return update(id, title, files, artistNames, thumbnail, clearThumbnail, registeredOn, source, true, description, keepImageUrlsJson, imageOrderJson);
    }

    private Archive update(long id, String title, MultipartFile file, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                           LocalDate registeredOn, ArchiveSource source, boolean applySource) throws IOException {
        List<MultipartFile> files = file == null || file.isEmpty() ? List.of() : List.of(file);
        return update(id, title, files, artistNames, thumbnail, clearThumbnail, registeredOn, source, applySource, null, null, null);
    }

    private Archive update(long id, String title, List<MultipartFile> files, String artistNames, MultipartFile thumbnail, boolean clearThumbnail,
                           LocalDate registeredOn, ArchiveSource source, boolean applySource, String description,
                           String keepImageUrlsJson, String imageOrderJson) throws IOException {
        Archive archive = show(id);
        validateThumbnail(thumbnail);
        if (thumbnail != null && !thumbnail.isEmpty() && archive.getType() != MediaType.VIDEO) {
            throw new IllegalArgumentException("영상 아카이브에만 썸네일을 설정할 수 있습니다.");
        }
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty() && archive.getType() == MediaType.VIDEO) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        archive.setTitle(safeTitle);
        if (description != null || archive.getType() == MediaType.IMAGE) {
            archive.setDescription(normalizeDescription(description));
        }
        if (artistNames != null) {
            archive.setArtistNames(artistNames.trim());
        }
        if (registeredOn != null) {
            archive.setRegisteredOn(registeredOn);
        }
        if (applySource) {
            archive.setSource(source);
        }
        List<MultipartFile> uploads = files == null ? List.of() : files.stream()
                .filter(part -> part != null && !part.isEmpty())
                .toList();
        if (archive.getType() == MediaType.IMAGE && (keepImageUrlsJson != null || imageOrderJson != null || !uploads.isEmpty())) {
            List<String> previous = ArchiveImages.resolve(archive, objectMapper);
            Map<String, SavedMeta> uploadedByUrl = new LinkedHashMap<>();
            var merged = ArchiveImages.merge(previous, keepImageUrlsJson, imageOrderJson, uploads, objectMapper, file -> {
                SavedMeta meta = fileStorageService.saveWithMeta(file);
                coerceType(MediaType.IMAGE, meta);
                uploadedByUrl.put(meta.getUrl(), meta);
                return meta.getUrl();
            });
            if (merged.urls().isEmpty()) {
                throw new IllegalArgumentException("사진은 최소 1장이 필요합니다.");
            }
            if (!merged.urls().equals(previous)) {
                for (String removed : merged.removed()) {
                    deleteStored(SavedMeta.builder().url(removed).build());
                }
                String cover = ArchiveImages.primary(merged.urls());
                SavedMeta current = archive.getFile();
                if (current == null || cover == null || !cover.equals(current.getUrl())) {
                    archive.setFile(coverFileMeta(cover, current, uploadedByUrl));
                }
                applyImageUrls(archive, merged.urls());
            }
        } else if (!uploads.isEmpty()) {
            SavedMeta previous = archive.getFile();
            SavedMeta meta = fileStorageService.saveWithMeta(uploads.get(0));
            coerceType(archive.getType(), meta);
            archive.setFile(meta);
            deleteStored(previous);
        }
        String previousThumbnail = archive.getThumbnailUrl();
        if (thumbnail != null && !thumbnail.isEmpty()) {
            archive.setThumbnailUrl(fileStorageService.saveWithMeta(thumbnail).getUrl());
            deleteThumbnail(previousThumbnail);
        } else if (clearThumbnail) {
            archive.setThumbnailUrl(null);
            deleteThumbnail(previousThumbnail);
        }
        return archiveRepository.save(archive);
    }

    private void applyImageUrls(Archive archive, List<String> urls) {
        List<String> safe = urls == null ? List.of() : urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .toList();
        archive.setImagesJson(ArchiveImages.write(safe, objectMapper));
    }

    /** 커버 URL이 바뀌어도 file_type 등 NOT NULL 메타가 비지 않게 채운다. */
    private SavedMeta coverFileMeta(String coverUrl, SavedMeta previous, Map<String, SavedMeta> uploadedByUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            throw new IllegalArgumentException("사진은 최소 1장이 필요합니다.");
        }
        SavedMeta uploaded = uploadedByUrl == null ? null : uploadedByUrl.get(coverUrl);
        if (uploaded != null) {
            return uploaded;
        }
        if (previous != null && coverUrl.equals(previous.getUrl())) {
            return previous;
        }
        return imageMetaFromUrl(coverUrl);
    }

    private static SavedMeta imageMetaFromUrl(String url) {
        String relative = url.startsWith("/uploads/") ? url.substring("/uploads/".length()) : url;
        String fileName = relative;
        int slash = relative.lastIndexOf('/');
        if (slash >= 0 && slash < relative.length() - 1) {
            fileName = relative.substring(slash + 1);
        }
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        String fileType = switch (ext) {
            case "", "jpg", "jpeg", "jfif" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "image/" + ext;
        };
        return SavedMeta.builder()
                .url(url)
                .savedName(relative)
                .originalName(fileName)
                .fileType(fileType)
                .size(0L)
                .mediaType(MediaType.IMAGE)
                .build();
    }

    private void deleteImageSet(Archive archive) {
        LinkedHashSet<String> urls = new LinkedHashSet<>(ArchiveImages.resolve(archive, objectMapper));
        if (archive.getFile() != null && archive.getFile().getUrl() != null) {
            urls.add(archive.getFile().getUrl());
        }
        for (String url : urls) {
            deleteStored(SavedMeta.builder().url(url).build());
        }
        if (archive.getFile() != null
                && archive.getFile().getSavedName() != null
                && !archive.getFile().getSavedName().isBlank()) {
            String coverUrl = archive.getFile().getUrl();
            if (coverUrl == null || !urls.contains(coverUrl)) {
                deleteStored(archive.getFile());
            }
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null) return null;
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 같은 날짜·출처·설명(미입력 포함)의 사진 게시글이 있으면 추가를 막는다. */
    private void ensureUniqueImagePost(LocalDate registeredOn, ArchiveSource source, String description) {
        if (registeredOn == null || source == null) {
            throw new IllegalArgumentException("날짜와 출처를 선택해 주세요.");
        }
        String key = normalizeDescription(description);
        String descriptionKey = key == null ? "" : key;
        if (archiveRepository.existsImageDuplicate(MediaType.IMAGE, registeredOn, source, descriptionKey)) {
            throw new IllegalArgumentException(
                    descriptionKey.isEmpty()
                            ? "같은 날짜·출처의 게시글이 이미 있습니다. 설명을 비운 게시글은 하나만 등록할 수 있습니다."
                            : "같은 날짜·출처·설명의 게시글이 이미 있습니다.");
        }
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()) return;
        String name = thumbnail.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|jfif|png|webp|gif|bmp)$")) {
            throw new IllegalArgumentException("썸네일은 JPG, JFIF, PNG, WebP, GIF, BMP 이미지 파일로 선택해 주세요.");
        }
    }

    private void deleteThumbnail(String url) {
        if (url != null && url.startsWith("/uploads/")) {
            deleteStored(SavedMeta.builder().url(url).build());
        }
    }

    private void deleteStored(SavedMeta file) {
        if (file == null) return;
        try {
            if (file.getSavedName() != null && !file.getSavedName().isBlank()) {
                fileStorageService.deleteFile(file.getSavedName());
            } else if (file.getUrl() != null && file.getUrl().startsWith("/uploads/")) {
                fileStorageService.deleteFile(file.getUrl());
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public Archive uploadImageOnly(MultipartFile file, String title) throws IOException {
        return save(file, title, MediaType.IMAGE);
    }

    @Transactional
    public Archive uploadVideoOnly(MultipartFile file, String title) throws IOException {
        return save(file, title, MediaType.VIDEO, "");
    }


    private MediaType coerceType(MediaType requested, SavedMeta meta) {
        boolean isImage = meta != null && meta.isImage();
        boolean isVideo = meta != null && meta.isVideo();

        if (requested == null) {
            if (isImage) return MediaType.IMAGE;
            if (isVideo) return MediaType.VIDEO;
            return MediaType.OTHER;
        }

        switch (requested) {
            case IMAGE -> {
                if (!isImage) throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
                return MediaType.IMAGE;
            }
            case VIDEO -> {
                if (!isVideo) throw new IllegalArgumentException("영상 파일만 업로드 가능합니다.");
                return MediaType.VIDEO;
            }
            default -> {
                return MediaType.OTHER;
            }
        }
    }
}

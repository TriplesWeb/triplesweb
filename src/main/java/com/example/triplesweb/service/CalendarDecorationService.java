package com.example.triplesweb.service;

import com.example.triplesweb.dto.CalendarDecorationDto;
import com.example.triplesweb.entity.CalendarDecoration;
import com.example.triplesweb.repository.CalendarDecorationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CalendarDecorationService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private final CalendarDecorationRepository repository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<CalendarDecorationDto> findAll() {
        return repository.findAllByOrderByEventDateAscIdAsc().stream().map(this::toDto).toList();
    }

    public CalendarDecorationDto create(String title, String kind, java.time.LocalDate date,
                                        String unitName, String description, String color, MultipartFile image) {
        CalendarDecoration item = new CalendarDecoration();
        apply(item, title, kind, date, unitName, description, color, image, false);
        return toDto(repository.save(item));
    }

    public CalendarDecorationDto update(long id, String title, String kind, java.time.LocalDate date,
                                        String unitName, String description, String color, MultipartFile image,
                                        boolean clearImage) {
        CalendarDecoration item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 달력 장식이 없습니다."));
        String previous = item.getImageUrl();
        apply(item, title, kind, date, unitName, description, color, image, clearImage);
        if (image != null && !image.isEmpty() || clearImage) deleteManagedImage(previous);
        return toDto(repository.save(item));
    }

    public void delete(long id) {
        CalendarDecoration item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 달력 장식이 없습니다."));
        deleteManagedImage(item.getImageUrl());
        repository.delete(item);
    }

    private void apply(CalendarDecoration item, String title, String kind, java.time.LocalDate date,
                       String unitName, String description, String color, MultipartFile image, boolean clearImage) {
        if (!StringUtils.hasText(title) || title.trim().length() > 120) throw new IllegalArgumentException("제목은 1~120자로 입력해 주세요.");
        if (!StringUtils.hasText(kind) || !(kind.equals("UNIT") || kind.equals("ANNIVERSARY"))) throw new IllegalArgumentException("구분을 선택해 주세요.");
        if (date == null) throw new IllegalArgumentException("날짜를 입력해 주세요.");
        item.setTitle(title.trim());
        item.setKind(kind);
        item.setEventDate(date);
        item.setUnitName(unitName == null || unitName.isBlank() ? null : unitName.trim());
        item.setDescription(description == null ? "" : description.trim());
        item.setColor(normalizeColor(color));
        if (image != null && !image.isEmpty()) {
            validateImage(image);
            try {
                item.setImageUrl(fileStorageService.saveWithMeta(image, "calendar").getUrl());
            } catch (IOException e) {
                throw new IllegalStateException("달력 이미지를 저장하지 못했습니다.", e);
            }
        } else if (clearImage) {
            item.setImageUrl(null);
        }
    }

    private void validateImage(MultipartFile image) {
        String filename = image.getOriginalFilename() == null ? "" : image.getOriginalFilename();
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(ext)) throw new IllegalArgumentException("PNG, JPG, WEBP 이미지만 업로드할 수 있습니다.");
        String type = image.getContentType();
        if (type != null && !type.startsWith("image/")) throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        if (image.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("이미지는 10MB 이하로 업로드해 주세요.");
        try {
            byte[] bytes = image.getBytes();
            boolean png = bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47;
            boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            boolean webp = bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            if (!(png || jpeg || webp)) throw new IllegalArgumentException("파일 내용이 PNG, JPG, WEBP 이미지 형식과 일치하지 않습니다.");
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
        }
    }

    private String normalizeColor(String value) {
        if (!StringUtils.hasText(value)) return "#00f5d4";
        String color = value.trim();
        if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("색상 형식이 올바르지 않습니다.");
        return color;
    }

    private void deleteManagedImage(String url) {
        if (url == null || !url.startsWith("/uploads/images/calendar/")) return;
        try { fileStorageService.deleteFile(url); } catch (RuntimeException ignored) { }
    }

    private CalendarDecorationDto toDto(CalendarDecoration item) {
        return new CalendarDecorationDto(item.getId(), item.getTitle(), item.getKind(), item.getEventDate(),
                item.getUnitName(), item.getDescription(), item.getImageUrl(), item.getColor());
    }
}

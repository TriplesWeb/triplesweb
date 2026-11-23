package com.example.triplesweb.service;

import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ArchiveSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArchiveSourceService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final String DEFAULT_COLOR = "#00f5d4";
    private static final long CACHE_TTL_MS = 30_000L;

    private final ArchiveSourceRepository archiveSourceRepository;
    private final ArchiveRepository archiveRepository;

    private final AtomicReference<CachedCatalog> cache = new AtomicReference<>();

    private record CachedCatalog(List<ArchiveSource> all, long at) {}

    @Transactional(readOnly = true)
    public List<ArchiveSourceDto> listVisible() {
        return loadAll().stream()
                .filter(ArchiveSource::isVisible)
                .map(ArchiveSourceDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArchiveSourceDto> listAll() {
        return loadAll().stream().map(ArchiveSourceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ArchiveSource requireVisible(String raw) {
        ArchiveSource source = requireExisting(raw);
        if (!source.isVisible()) {
            throw new IllegalArgumentException("숨긴 출처는 선택할 수 없습니다.");
        }
        return source;
    }

    /** 업로드·수정 공통. 숨긴 출처도 기존 값 유지용으로 허용한다. */
    @Transactional(readOnly = true)
    public ArchiveSource requireExisting(String raw) {
        ArchiveSource source = resolve(raw);
        if (source == null) {
            throw new IllegalArgumentException("출처를 선택하세요.");
        }
        return source;
    }

    /** 필터·기존 값 해석. 없거나 잘못된 값은 null. */
    @Transactional(readOnly = true)
    public ArchiveSource find(String raw) {
        return resolve(raw);
    }

    @Transactional(readOnly = true)
    public String resolveCode(String raw) {
        ArchiveSource source = resolve(raw);
        return source == null ? null : source.getCode();
    }

    @Transactional(readOnly = true)
    public Map<String, ArchiveSource> byCode() {
        Map<String, ArchiveSource> map = new LinkedHashMap<>();
        for (ArchiveSource source : loadAll()) {
            map.put(source.getCode().toUpperCase(Locale.ROOT), source);
        }
        return map;
    }

    @Transactional
    public ArchiveSourceDto create(String code, String label, String color, Integer sortOrder, Boolean visible) {
        String normalized = normalizeNewCode(code);
        if (archiveSourceRepository.existsByCodeIgnoreCase(normalized)) {
            throw new IllegalArgumentException("이미 있는 출처 코드입니다.");
        }
        ArchiveSource source = new ArchiveSource();
        source.setCode(normalized);
        source.setLabel(requireLabel(label));
        source.setColor(normalizeColor(color));
        source.setSortOrder(sortOrder == null ? nextSortOrder() : sortOrder);
        source.setVisible(visible == null || visible);
        ArchiveSource saved = archiveSourceRepository.save(source);
        invalidate();
        return ArchiveSourceDto.from(saved);
    }

    @Transactional
    public ArchiveSourceDto update(long id, String label, String color, Integer sortOrder, Boolean visible) {
        ArchiveSource source = archiveSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("출처를 찾을 수 없습니다."));
        if (label != null) source.setLabel(requireLabel(label));
        if (color != null && !color.isBlank()) source.setColor(normalizeColor(color));
        if (sortOrder != null) source.setSortOrder(sortOrder);
        if (visible != null) source.setVisible(visible);
        ArchiveSource saved = archiveSourceRepository.save(source);
        invalidate();
        return ArchiveSourceDto.from(saved);
    }

    @Transactional
    public void delete(long id) {
        ArchiveSource source = archiveSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("출처를 찾을 수 없습니다."));
        long used = archiveRepository.countBySourceIgnoreCase(source.getCode());
        if (used > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이 출처를 쓰는 아카이브가 " + used + "건 있어 삭제할 수 없습니다. 숨김으로 바꿔 주세요.");
        }
        archiveSourceRepository.delete(source);
        invalidate();
    }

    public void invalidate() {
        cache.set(null);
    }

    private ArchiveSource resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if ("teaser".equalsIgnoreCase(value)) value = "TEASER_BEHIND";
        String upper = value.toUpperCase(Locale.ROOT).replace('-', '_');
        String lower = value.toLowerCase(Locale.ROOT);
        for (ArchiveSource source : loadAll()) {
            if (source.getCode().equalsIgnoreCase(upper) || source.getSlug().equalsIgnoreCase(lower)) {
                return source;
            }
        }
        return null;
    }

    private List<ArchiveSource> loadAll() {
        long now = System.currentTimeMillis();
        CachedCatalog cached = cache.get();
        if (cached != null && now - cached.at() < CACHE_TTL_MS) {
            return cached.all();
        }
        List<ArchiveSource> all = new ArrayList<>(archiveSourceRepository.findAllByOrderBySortOrderAscIdAsc());
        cache.set(new CachedCatalog(List.copyOf(all), now));
        return all;
    }

    private int nextSortOrder() {
        return loadAll().stream().mapToInt(ArchiveSource::getSortOrder).max().orElse(0) + 10;
    }

    private static String normalizeNewCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("출처 코드를 입력하세요.");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("출처 코드는 영문 대문자·숫자·밑줄만 쓸 수 있습니다.");
        }
        return code;
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("출처 이름을 입력하세요.");
        }
        String trimmed = label.trim();
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("출처 이름은 64자까지입니다.");
        }
        return trimmed;
    }

    private static String normalizeColor(String color) {
        if (color == null || color.isBlank()) return DEFAULT_COLOR;
        String value = color.trim();
        if (!COLOR_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("색상은 #RRGGBB 형식이어야 합니다.");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

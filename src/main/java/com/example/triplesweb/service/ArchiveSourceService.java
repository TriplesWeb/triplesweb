package com.example.triplesweb.service;

import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ArchiveSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArchiveSourceService {

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final String DEFAULT_COLOR = "#00f5d4";
    private static final Comparator<ArchiveSource> SOURCE_ORDER = Comparator
            .comparingInt(ArchiveSource::getSortOrder).thenComparing(ArchiveSource::getId);
    private static final long CACHE_TTL_MS = 30_000L;

    private final ArchiveSourceRepository archiveSourceRepository;
    private final ArchiveRepository archiveRepository;

    private final AtomicReference<CachedCatalog> cache = new AtomicReference<>();

    private record CachedCatalog(List<ArchiveSource> all, long at) {}

    public record SourceTab(ArchiveSourceDto source, boolean hasPosts) {}

    @Transactional(readOnly = true)
    public List<SourceTab> listTabs(MediaType type, boolean includeEmpty) {
        return listTabs(archiveRepository.findSourceCodesWithPosts(type), includeEmpty);
    }

    @Transactional(readOnly = true)
    public List<SourceTab> listTabs(Collection<String> sourceCodesWithPosts, boolean includeEmpty) {
        Set<String> used = new HashSet<>();
        sourceCodesWithPosts.forEach(code -> used.add(code.toUpperCase(Locale.ROOT)));
        if (used.contains("TEASER")) used.add("TEASER_BEHIND");
        return loadAll().stream()
                .map(source -> new SourceTab(ArchiveSourceDto.from(source), used.contains(source.getCode().toUpperCase(Locale.ROOT))))
                .filter(tab -> includeEmpty || tab.hasPosts())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArchiveSourceDto> listAll() {
        return loadAll().stream().map(ArchiveSourceDto::from).toList();
    }

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
    public ArchiveSourceDto create(String label, String color, Integer sortOrder) {
        ArchiveSource source = new ArchiveSource();
        source.setLabel(requireLabel(label));
        source.setCode(nextSourceCode());
        source.setColor(normalizeColor(color));
        source.setSortOrder(sortOrder == null ? nextSortOrder() : sortOrder);
        ArchiveSource saved = archiveSourceRepository.save(source);
        invalidate();
        return ArchiveSourceDto.from(saved);
    }

    @Transactional
    public ArchiveSourceDto update(long id, String label, String color, Integer sortOrder) {
        ArchiveSource source = archiveSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("출처를 찾을 수 없습니다."));
        if (label != null) source.setLabel(requireLabel(label));
        if (color != null && !color.isBlank()) source.setColor(normalizeColor(color));
        if (sortOrder != null) source.setSortOrder(sortOrder);
        ArchiveSource saved = archiveSourceRepository.save(source);
        invalidate();
        return ArchiveSourceDto.from(saved);
    }

    @Transactional
    public List<ArchiveSourceDto> move(long id, int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("이동 방향은 -1 또는 1이어야 합니다.");
        }
        List<ArchiveSource> sources = new ArrayList<>(archiveSourceRepository.findAllForReorder());
        int index = 0;
        while (index < sources.size() && sources.get(index).getId() != id) {
            index++;
        }
        if (index == sources.size()) {
            throw new IllegalArgumentException("출처를 찾을 수 없습니다.");
        }
        int neighborIndex = index + direction;
        if (neighborIndex >= 0 && neighborIndex < sources.size()) {
            ArchiveSource source = sources.get(index);
            ArchiveSource neighbor = sources.get(neighborIndex);
            int order = source.getSortOrder();
            source.setSortOrder(neighbor.getSortOrder());
            neighbor.setSortOrder(order);
            Collections.swap(sources, index, neighborIndex);
            ensureSortOrder(sources);
            // 행 잠금이 풀려 다른 요청이 변경을 읽을 수 있을 때 캐시를 비운다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        }
        return sources.stream().map(ArchiveSourceDto::from).toList();
    }

    private void ensureSortOrder(List<ArchiveSource> sources) {
        for (int i = 1; i < sources.size(); i++) {
            ArchiveSource previous = sources.get(i - 1);
            ArchiveSource current = sources.get(i);
            if (SOURCE_ORDER.compare(previous, current) <= 0) continue;
            // 동점인 경우에만 후속 순서값을 올려 실제 조회 순서와 일치시킨다.
            if (previous.getSortOrder() == Integer.MAX_VALUE) {
                for (int j = 0; j < sources.size(); j++) {
                    sources.get(j).setSortOrder(j + 1);
                }
                return;
            }
            current.setSortOrder(previous.getSortOrder() + 1);
        }
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
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        // 레거시 TEASER → 정식 TEASER_BEHIND (카탈로그에 TEASER가 있어도 통합 코드를 쓴다).
        if ("TEASER".equals(normalized)) {
            return findInCatalog("TEASER_BEHIND");
        }
        return findInCatalog(value);
    }

    private ArchiveSource findInCatalog(String value) {
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

    private String nextSourceCode() {
        // Base 36 keeps the entire UUID within 25 characters and leaves room for the prefix.
        String code;
        do {
            String hex = UUID.randomUUID().toString().replace("-", "");
            code = "SOURCE_" + new BigInteger(hex, 16).toString(36).toUpperCase(Locale.ROOT);
        } while (archiveSourceRepository.existsByCodeIgnoreCase(code));
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

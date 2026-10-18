package com.example.triplesweb.service;

import com.example.triplesweb.entity.Chemistry;
import com.example.triplesweb.repository.ChemistryRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * classpath {@code data/chemistry-seed.json}의 페어를 DB에 넣는다.
 * 없는 페어만 insert하고, 이미 있는 행의 라벨·설명은 덮어쓰지 않는다.
 * 다만 imageUrl이 비어 있고 시드에 사진이 있으면 사진만 채운다(예전 수정 버그 복구).
 * 기본은 비활성 — {@code chemistry.seed.enabled=true} 일 때만 실행한다.
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class ChemistrySeedService implements ApplicationRunner {

    private final ChemistryRepository chemistryRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${chemistry.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        purgeLegacySoftDeleted();
        if (!seedEnabled) {
            log.debug("Chemistry seed disabled (chemistry.seed.enabled=false)");
            return;
        }
        ClassPathResource resource = new ClassPathResource("data/chemistry-seed.json");
        if (!resource.exists()) {
            log.debug("chemistry seed file missing — skip");
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            SeedFile seed = objectMapper.readValue(in, SeedFile.class);
            if (seed == null || seed.getPairs() == null || seed.getPairs().isEmpty()) {
                return;
            }
            int inserted = 0;
            int backfilledImages = 0;
            for (SeedPair pair : seed.getPairs()) {
                if (!StringUtils.hasText(pair.getMemberA())
                        || !StringUtils.hasText(pair.getMemberB())
                        || !StringUtils.hasText(pair.getLabel())) {
                    continue;
                }
                String a = pair.getMemberA().trim();
                String b = pair.getMemberB().trim();
                String seedImage = StringUtils.hasText(pair.getImageUrl()) ? pair.getImageUrl().trim() : null;
                var existing = chemistryRepository.findByMemberAAndMemberB(a, b);
                if (existing.isPresent()) {
                    // 예전 오버레이 수정으로 imageUrl만 비어 있는 행 복구 (라벨·설명은 건드리지 않음)
                    Chemistry row = existing.get();
                    if (!StringUtils.hasText(row.getImageUrl()) && seedImage != null) {
                        row.setImageUrl(seedImage);
                        chemistryRepository.save(row);
                        backfilledImages++;
                    }
                    continue;
                }
                Chemistry entity = Chemistry.builder()
                        .memberA(a)
                        .memberB(b)
                        .label(pair.getLabel())
                        .description(pair.getDescription() != null ? pair.getDescription() : "")
                        .imageUrl(seedImage)
                        .linksJson(toLinksJson(pair.getLinks()))
                        .crossFlag(pair.isCrossFlag())
                        .build();
                chemistryRepository.save(entity);
                inserted++;
            }
            if (inserted > 0 || backfilledImages > 0) {
                log.info("Chemistry seed: inserted={}, backfilledImages={}", inserted, backfilledImages);
            } else {
                log.info("Chemistry seed enabled but no new pairs to insert");
            }
        } catch (Exception e) {
            log.warn("Chemistry seed skipped: {}", e.getMessage());
        }
    }

    /** 예전 soft-delete 무덤 행을 물리 삭제한다. deleted 컬럼이 없으면 무시. */
    private void purgeLegacySoftDeleted() {
        try {
            int n = entityManager.createNativeQuery("DELETE FROM Chemistry WHERE deleted = 1").executeUpdate();
            if (n > 0) {
                log.info("Purged {} legacy soft-deleted chemistry rows", n);
            }
        } catch (RuntimeException e) {
            log.debug("Soft-delete purge skipped: {}", e.getMessage());
        }
    }

    private String toLinksJson(Map<String, String> links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        links.forEach((k, v) -> {
            if (StringUtils.hasText(k) && StringUtils.hasText(v)) {
                cleaned.put(k.trim(), v.trim());
            }
        });
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedFile {
        private List<String> members;
        private List<SeedPair> pairs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedPair {
        private String memberA;
        private String memberB;
        private String label;
        private String description;
        private String imageUrl;
        private Map<String, String> links;
        private boolean crossFlag;
    }
}

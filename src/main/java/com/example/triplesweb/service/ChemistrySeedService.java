package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Chemistry;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ChemistryRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
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
 * classpath {@code data/chemistry-seed.json} 페어를 Artist FK 스키마로 적재한다.
 * {@link com.example.triplesweb.config.DataInitializer}(@Order 1) 이후 실행.
 * 없는 페어만 insert — 기존 라벨·설명은 덮어쓰지 않는다.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ChemistrySeedService implements CommandLineRunner {

    private final ChemistryRepository chemistryRepository;
    private final ArtistRepository artistRepository;
    private final ObjectMapper objectMapper;
    private final ChemistryService chemistryService;

    @Value("${chemistry.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        long existing = chemistryRepository.count();
        boolean empty = existing == 0;
        if (!empty && !seedEnabled) {
            log.debug("Chemistry seed skipped (count={}, enabled=false)", existing);
            return;
        }
        int inserted = reseedMissingPairs();
        if (inserted == 0 && empty) {
            log.warn("Chemistry table still empty after seed — Artist 이름/시드 JSON을 확인하세요.");
        }
    }

    /**
     * 시드 JSON 기준 누락 페어만 Artist FK로 insert.
     * @return 새로 넣은 행 수
     */
    @Transactional
    public int reseedMissingPairs() {
        if (artistRepository.count() == 0) {
            log.warn("Chemistry seed skipped: Artist 테이블이 비어 있습니다.");
            return 0;
        }
        ClassPathResource resource = new ClassPathResource("data/chemistry-seed.json");
        if (!resource.exists()) {
            log.warn("chemistry-seed.json missing — skip");
            return 0;
        }
        try (InputStream in = resource.getInputStream()) {
            SeedFile seed = objectMapper.readValue(in, SeedFile.class);
            if (seed == null || seed.getPairs() == null || seed.getPairs().isEmpty()) {
                return 0;
            }
            int inserted = 0;
            int backfilledImages = 0;
            int skipped = 0;
            for (SeedPair pair : seed.getPairs()) {
                if (!StringUtils.hasText(pair.getMemberA())
                        || !StringUtils.hasText(pair.getMemberB())
                        || !StringUtils.hasText(pair.getLabel())) {
                    skipped++;
                    continue;
                }
                Artist[] ordered;
                try {
                    ordered = chemistryService.resolveArtistPair(pair.getMemberA(), pair.getMemberB());
                } catch (IllegalArgumentException e) {
                    log.warn("Chemistry seed skip: {}", e.getMessage());
                    skipped++;
                    continue;
                }

                String seedImage = StringUtils.hasText(pair.getImageUrl()) ? pair.getImageUrl().trim() : null;
                var existingPair = chemistryRepository.findByArtistPair(
                        ordered[0].getArtistId(), ordered[1].getArtistId());
                if (existingPair.isPresent()) {
                    Chemistry row = existingPair.get();
                    if (!StringUtils.hasText(row.getImageUrl()) && seedImage != null) {
                        row.setImageUrl(seedImage);
                        chemistryRepository.save(row);
                        backfilledImages++;
                    }
                    continue;
                }
                Chemistry entity = Chemistry.builder()
                        .artistA(ordered[0])
                        .artistB(ordered[1])
                        .label(pair.getLabel().trim())
                        .description(pair.getDescription() != null ? pair.getDescription() : "")
                        .imageUrl(seedImage)
                        .linksJson(toLinksJson(pair.getLinks()))
                        .crossFlag(pair.isCrossFlag())
                        .build();
                chemistryRepository.save(entity);
                inserted++;
            }
            log.info("Chemistry seed: inserted={}, backfilledImages={}, skipped={}, totalNow={}",
                    inserted, backfilledImages, skipped, chemistryRepository.count());
            return inserted;
        } catch (Exception e) {
            log.error("Chemistry seed failed: {}", e.getMessage(), e);
            return 0;
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

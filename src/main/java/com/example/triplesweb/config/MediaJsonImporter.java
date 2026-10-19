package com.example.triplesweb.config;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Media;
import com.example.triplesweb.repository.MediaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기존 JSON 카탈로그({@code media.catalog.path})가 있고 Media 테이블이 비어 있으면 1회 이관한다.
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
public class MediaJsonImporter implements ApplicationRunner {

    private static final List<String> TABS = List.of("mv", "signal", "behind", "secret", "contents");

    private final MediaRepository mediaRepository;
    private final ObjectMapper objectMapper;

    @Value("${media.catalog.path:}")
    private String catalogPath;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mediaRepository.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(catalogPath)) {
            return;
        }
        Path path = Paths.get(catalogPath);
        if (!Files.isRegularFile(path)) {
            log.debug("Media JSON catalog missing — skip import: {}", path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, List<MediaDto>> data = objectMapper.readValue(
                    reader, new TypeReference<LinkedHashMap<String, List<MediaDto>>>() {});
            if (data == null || data.isEmpty()) {
                return;
            }
            int imported = 0;
            for (String tab : TABS) {
                List<MediaDto> list = data.getOrDefault(tab, List.of());
                long order = 0;
                for (MediaDto dto : list) {
                    if (dto == null || !StringUtils.hasText(dto.getYoutubeId())) {
                        continue;
                    }
                    String ytId = dto.getYoutubeId().trim();
                    if (mediaRepository.findByYoutubeIdIgnoreCase(ytId).isPresent()) {
                        continue;
                    }
                    Media entity = Media.builder()
                            .youtubeId(ytId)
                            .tab(tab)
                            .mediaTitle(dto.getTitle() != null ? dto.getTitle() : "")
                            .mediaDur(dto.getDuration() != null ? dto.getDuration() : "")
                            .viewCount(dto.getViews() != null ? dto.getViews() : 0L)
                            .description(dto.getDescription())
                            .sortOrder(order++)
                            .build();
                    mediaRepository.save(entity);
                    imported++;
                }
            }
            if (imported > 0) {
                log.info("Imported {} media rows from JSON catalog {}", imported, path);
            }
        } catch (Exception e) {
            log.warn("Media JSON import skipped: {}", e.getMessage());
        }
    }
}

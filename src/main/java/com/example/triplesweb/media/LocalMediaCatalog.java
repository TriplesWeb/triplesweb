package com.example.triplesweb.media;

import com.example.triplesweb.dto.MediaDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Applies local catalog additions and corrections to the normal database results. */
@Slf4j
@Component
public class LocalMediaCatalog {
    private final Map<String, Video> videos;

    public LocalMediaCatalog(ObjectMapper mapper,
                             @Value("${media.local-catalog.path:${MEDIA_LOCAL_CATALOG_PATH:}}") String path) {
        videos = read(mapper, path);
    }

    public boolean hasTab(String tab) {
        return videos.values().stream().anyMatch(video -> !video.hidden() && tab.equals(video.tab()));
    }

    public boolean affectsTab(String tab) {
        return hasTab(tab) || videos.values().stream().anyMatch(video -> video.hidden() || video.changesTab());
    }

    public boolean isHidden(String youtubeId) {
        Video local = videos.get(key(youtubeId));
        return local != null && local.hidden();
    }

    public MediaDto applyTabOverride(MediaDto stored) {
        Video local = videos.get(key(stored.getYoutubeId()));
        if (local != null && local.changesTab()) {
            stored.setTab(local.tab());
            if (local.isCuratedFancam()) {
                String names = Stream.of(stored.getArtistNames(), local.artistNames())
                        .filter(value -> value != null && !value.isBlank())
                        .flatMap(value -> Stream.of(value.split(",")))
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .distinct()
                        .collect(Collectors.joining(", "));
                stored.setArtistNames(names);
            }
        }
        return stored;
    }

    public Optional<MediaDto> findOne(String youtubeId) {
        return Optional.ofNullable(videos.get(key(youtubeId))).filter(video -> !video.hidden()).map(Video::toDto);
    }

    public List<MediaDto> appendMissing(List<MediaDto> stored) {
        if (videos.isEmpty()) return stored;
        var merged = new LinkedHashMap<String, MediaDto>();
        for (MediaDto video : stored) {
            if (!isHidden(video.getYoutubeId())) merged.putIfAbsent(key(video.getYoutubeId()), video);
        }
        videos.forEach((id, video) -> {
            if (!video.hidden() && !merged.containsKey(id)) merged.put(id, video.toDto());
        });
        return List.copyOf(merged.values());
    }

    private static Map<String, Video> read(ObjectMapper mapper, String path) {
        if (path.isBlank() || !Files.isRegularFile(Path.of(path))) return Map.of();
        var result = new LinkedHashMap<String, Video>();
        try (var reader = Files.newBufferedReader(Path.of(path))) {
            List<Video> entries = mapper.readValue(reader, new TypeReference<>() {});
            if (entries == null) return Map.of();
            for (Video entry : entries) {
                if (entry != null && entry.youtubeId() != null && entry.youtubeId().matches("[A-Za-z0-9_-]{11}")
                        && (entry.hidden() || (entry.tab() != null && entry.title() != null && !entry.title().isBlank()))) {
                    result.putIfAbsent(key(entry.youtubeId()), entry);
                }
            }
            log.info("Loaded {} local media videos", result.size());
            return result;
        } catch (IOException ex) {
            log.warn("Could not read local media catalog: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static String key(String youtubeId) {
        return youtubeId == null ? "" : youtubeId.toLowerCase(Locale.ROOT);
    }

    private record Video(String youtubeId, String tab, String title, String duration, long views,
                         String description, String artistNames, String publishedOn, boolean hidden, boolean overrideTab) {
        private boolean changesTab() {
            return !hidden && (overrideTab || MediaTab.MUSIC.getKey().equals(tab));
        }

        private boolean isCuratedFancam() {
            return overrideTab && MediaTab.FANCAM.getKey().equals(tab);
        }

        private MediaDto toDto() {
            return MediaDto.builder().youtubeId(youtubeId).tab(MediaTabClassifier.correctMvTab(tab, title)).title(title).duration(duration)
                    .views(views).viewCountFmt(NumberFormat.getInstance().format(views))
                    .description(description).artistNames(artistNames)
                    .publishedOn(publishedOn).build();
        }
    }
}

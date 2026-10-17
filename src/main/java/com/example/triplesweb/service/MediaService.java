package com.example.triplesweb.service;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.infra.YoutubeOembedClient;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.repository.MediaRepository;
import com.example.triplesweb.util.ISODuration;

import com.example.triplesweb.util.YoutubeIdExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * JSON 파일 기반 미디어 카탈로그(mv, signal 등 탭) 읽기·쓰기를 담당한다.
 * 탭별 페이징 조회, YouTube ID 기준 단건·연관 영상, 추가·삭제를 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MediaService {

    private final ObjectMapper mapper;
    private final MediaRepository mediaRepository;
    private final YoutubeOembedClient oembed;
    private final YoutubeDataApiClient yt;

    @Value("${media.catalog.path}")
    private String catalogPath;

    public Map<String, List<MediaDto>> readAll() throws IOException {
        Path p = Paths.get(catalogPath);
        if (!Files.exists(p)) {
            // 최초 생성: 탭만 빈 배열로
            Map<String, List<MediaDto>> empty = new LinkedHashMap<>();
            empty.put("mv", new ArrayList<>());
            empty.put("signal", new ArrayList<>());
            empty.put("behind", new ArrayList<>());
            empty.put("secret", new ArrayList<>());
            empty.put("contents", new ArrayList<>());
            writeAll(empty);
            return empty;
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            TypeReference<LinkedHashMap<String, List<MediaDto>>> type = new TypeReference<>() {};
            Map<String, List<MediaDto>> data = mapper.readValue(r, type);

            for (String k : List.of("mv","signal","behind","secret","contents")) {
                data.putIfAbsent(k, new ArrayList<>());
            }
            return data;
        }
    }

    private synchronized void writeAll(Map<String, List<MediaDto>> data) throws IOException {
        Path p = Paths.get(catalogPath);
        Files.createDirectories(p.getParent());
        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(w, data);
        }
    }

    public List<MediaDto> pageByTab(String tab, int page, int size) throws IOException {
        List<MediaDto> list = readAll().getOrDefault(tab, List.of());
        int from = Math.max(0, (page - 1) * size);
        int to   = Math.min(list.size(), from + size);
        if (from >= to) return List.of();
        return list.subList(from, to);
    }

    public Optional<MediaDto> findOne(MediaTab tab, String youtubeId) throws IOException {
        return readAll().getOrDefault(tab.getKey(), List.of()).stream()
                .filter(v -> youtubeId.equalsIgnoreCase(v.getYoutubeId()))
                .findFirst();
    }

    public List<MediaDto> related(MediaTab tab, String excludeId, int limit) throws IOException {
        return readAll().getOrDefault(tab.getKey(), List.of()).stream()
                .filter(v -> !v.getYoutubeId().equalsIgnoreCase(excludeId))
                .limit(limit)
                .toList();
    }

    public int totalByTab(String tab) throws IOException {
        return readAll().getOrDefault(tab, List.of()).size();
    }

    public synchronized void addMedia(String tab, MediaDto dto, boolean addFirst) throws IOException {
        if (dto.getYoutubeId() == null || dto.getYoutubeId().isBlank()) {
            return; // 잘못된 입력은 무시
        }
        Map<String, List<MediaDto>> data = readAll();
        List<MediaDto> list = data.getOrDefault(tab, new ArrayList<>());
        // 중복 방지(같은 youtubeId 제거)
        list.removeIf(v -> v.getYoutubeId().equalsIgnoreCase(dto.getYoutubeId()));

        if (addFirst) list.add(0, dto); else list.add(dto);
        data.put(tab, list);
        writeAll(data);
    }

    public synchronized boolean remove(String tab, String youtubeId) throws IOException {
        Map<String, List<MediaDto>> data = readAll();
        List<MediaDto> list = data.getOrDefault(tab, new ArrayList<>());
        boolean changed = list.removeIf(v -> v.getYoutubeId().equalsIgnoreCase(youtubeId));
        if (changed) writeAll(data);
        return changed;
    }

    /**
     * 💡 [유튜브 정보 자동 조회 기능 통합]
     * URL이나 ID를 받아 oEmbed 및 YouTube Data API를 통해
     * 제목, 썸네일, 영상 길이(duration), 조회수 등을 안전하게 추출·조합합니다.
     */
    public Map<String, Object> makeYoutubeInfo(String urlOrId) {
        String id = YoutubeIdExtractor.extract(urlOrId);
        if (id == null || id.isBlank()) {
            id = urlOrId.trim();
        }
        if (id.length() != 11) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("youtubeId", urlOrId != null ? urlOrId : "");
            fallback.put("title", "올바르지 않은 유튜브 ID 또는 링크입니다.");
            fallback.put("thumbnail", "");
            fallback.put("duration", "03:00");
            fallback.put("views", 0L);
            return fallback;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("youtubeId", id);
        res.put("title", "유튜브 영상 (" + id + ")");
        res.put("thumbnail", "https://img.youtube.com/vi/" + id + "/hqdefault.jpg");
        res.put("duration", "03:00"); // 기본 Fallback 길이
        res.put("views", 0L);

        // 1. oEmbed API 호출 (제목, 썸네일 보완)
        try {
            var oe = oembed.fetch("https://www.youtube.com/watch?v=" + id);
            if (oe != null) {
                if (oe.getTitle() != null && !oe.getTitle().isBlank()) {
                    res.put("title", oe.getTitle());
                }
                if (oe.getThumbnail_url() != null && !oe.getThumbnail_url().isBlank()) {
                    res.put("thumbnail", oe.getThumbnail_url());
                }
            }
        } catch (Exception ignored) {}

        // 2. YouTube Data API 호출 (정밀 제목, 영상 길이, 조회수 보완)
        try {
            var vr = yt.fetchVideo(id);
            if (vr != null && vr.getItems() != null && vr.getItems().length > 0) {
                var item = vr.getItems()[0];
                if (item.getSnippet() != null) {
                    if (item.getSnippet().getTitle() != null && !item.getSnippet().getTitle().isBlank()) {
                        res.put("title", item.getSnippet().getTitle());
                    }
                    if (item.getSnippet().getDescription() != null) {
                        res.put("description", item.getSnippet().getDescription());
                    }
                    String pub = item.getSnippet().getPublishedAt();
                    if (pub != null && pub.length() >= 10) {
                        res.put("publishedAt", pub.substring(0, 10).replace("-", "."));
                    }
                }
                if (item.getContentDetails() != null && item.getContentDetails().getDuration() != null) {
                    String clockDuration = ISODuration.toClock(item.getContentDetails().getDuration());
                    if (clockDuration != null && !clockDuration.isBlank()) {
                        res.put("duration", clockDuration);
                    }
                }
                if (item.getStatistics() != null && item.getStatistics().getViewCount() != null) {
                    res.put("views", item.getStatistics().getViewCount());
                }
            }
        } catch (Exception ignored) {}

        return res;
    }
    public synchronized void registerMedia(String tab, MediaDto form) throws IOException {
        if (form == null || form.getYoutubeId() == null || form.getYoutubeId().isBlank()) {
            return;
        }

        // 1. 순수 11자리 ID 추출
        String rawId = form.getYoutubeId().trim();
        String ytId = YoutubeIdExtractor.extract(rawId);
        if (ytId == null || ytId.isBlank()) {
            ytId = "Unknown";
        }

        String title = form.getTitle() == null ? "" : form.getTitle().trim();
        String dur = form.getDuration() == null ? "" : form.getDuration().trim();
        long views = form.getViews();
        String description = form.getDescription() == null ? "" : form.getDescription().trim();

        // 2. 사용자가 제목이나 길이를 입력하지 않았다면, 앞서 만든 makeYoutubeInfo로 자동 완성
        if (!ytId.isBlank() && (title.isBlank() || dur.isBlank() || dur.equals("03:00"))) {
            try {
                Map<String, Object> info = makeYoutubeInfo(ytId);
                if (title.isBlank() && info.get("title") != null) {
                    title = (String) info.get("title");
                }
                if ((dur.isBlank() || dur.equals("03:00")) && info.get("duration") != null) {
                    dur = (String) info.get("duration");
                }
                if (views == 0 && info.get("views") != null) {
                    views = Long.parseLong(info.get("views").toString());
                }
                if (description.isBlank() && info.get("description") != null) {
                    description = (String) info.get("description");
                }
            } catch (Exception ignored) {}
        }
        String formattedViews = java.text.NumberFormat.getInstance().format(views);

        // 3. 최종 DTO 생성 후 기존 addMedia 호출
        MediaDto dto = MediaDto.builder()
                .youtubeId(ytId)
                .title(title)
                .duration(dur)
                .views(views)
                .viewCountFmt(formattedViews)
                .description(description)
                .build();

        addMedia(tab, dto, form.isAddFirst());
    }

    private String extractSafeVideoId(String input) {
        if (input == null || input.isBlank()) { return null; }

        String val = input.trim();

        if (val.matches("^[a-zA-Z0-9_-]{11}$")) {
            return val;
        }

        if (val.contains("v=")) {
            int idx = val.indexOf("v=") + 2;
            String sub =  val.substring(idx);
            if (sub.startsWith("&")) sub = sub.substring(0,sub.indexOf("&"));
            if (sub.startsWith("?")) sub = sub.substring(0,sub.indexOf("?"));
            if (sub.length() >= 11) return sub.substring(0, 11);
            return sub;
        }

        if (val.contains("/embed/") || val.contains("/shorts/")) {
            String delim = val.contains("/embed/") ? "/embed/" : "/shorts/";
            int idx = val.indexOf(delim) + delim.length();
            String sub = val.substring(idx);
            if (sub.contains("?")) sub = sub.substring(0,sub.indexOf("?"));
            if (sub.contains("&")) sub = sub.substring(0,sub.indexOf("&"));
            if (sub.length() >= 11) return sub.substring(0, 11);
            return sub;
        }
        if (val.contains("?")) {
            val = val.substring(0, val.indexOf("?"));
        }
        if (val.length() >= 11) {
            return val.substring(val.length() - 11);
        }
        return val;
    }
}
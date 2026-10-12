package com.example.triplesweb.service;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.media.MediaTab;
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

@Service
@RequiredArgsConstructor
@Transactional
public class MediaService {

    private final ObjectMapper mapper;
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
    public synchronized void add(String tab, MediaDto dto, boolean addFirst) throws IOException {
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
}

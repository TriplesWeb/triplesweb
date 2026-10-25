package com.example.triplesweb.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 케미(페어·그룹) 다중 이미지 URL 목록 직렬화·정규화.
 * {@code image_url}은 첫 장 호환용, {@code images_json}이 정본이다.
 */
public final class ChemistryImages {

    public static final int MAX_IMAGES = 10;

    private ChemistryImages() {}

    public static List<String> resolve(String imagesJson, String imageUrl, ObjectMapper mapper) {
        List<String> fromJson = parse(imagesJson, mapper);
        if (!fromJson.isEmpty()) {
            return fromJson;
        }
        if (StringUtils.hasText(imageUrl)) {
            return List.of(imageUrl.trim());
        }
        return List.of();
    }

    public static List<String> parse(String imagesJson, ObjectMapper mapper) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(imagesJson)) {
            return out;
        }
        try {
            List<String> raw = mapper.readValue(imagesJson, new TypeReference<>() {});
            if (raw == null) {
                return out;
            }
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (String u : raw) {
                if (!StringUtils.hasText(u)) {
                    continue;
                }
                String trimmed = u.trim();
                if (seen.add(trimmed)) {
                    out.add(trimmed);
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 목록 형식이 올바르지 않습니다.");
        }
    }

    public static String write(List<String> urls, ObjectMapper mapper) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(urls);
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 목록 형식이 올바르지 않습니다.");
        }
    }

    public static String primary(List<String> urls) {
        return urls == null || urls.isEmpty() ? null : urls.get(0);
    }

    public static List<MultipartFile> collectUploads(MultipartFile single, List<MultipartFile> many) {
        List<MultipartFile> out = new ArrayList<>();
        if (many != null) {
            for (MultipartFile f : many) {
                if (f != null && !f.isEmpty()) {
                    out.add(f);
                }
            }
        }
        if (single != null && !single.isEmpty()) {
            out.add(single);
        }
        return out;
    }

    /**
     * keep 목록(기존 URL, 순서 유지) + 신규 업로드로 최종 목록을 만든다.
     * keep에 없는 기존 URL은 제거 대상으로 반환한다.
     */
    public static Result merge(
            List<String> previous,
            String keepImageUrlsJson,
            List<MultipartFile> uploads,
            boolean clearAll,
            ObjectMapper mapper,
            UploadFn uploadFn
    ) throws IOException {
        List<String> prev = previous != null ? previous : List.of();
        if (clearAll && uploads.isEmpty()) {
            return new Result(List.of(), prev);
        }

        LinkedHashSet<String> prevSet = new LinkedHashSet<>(prev);
        List<String> kept = new ArrayList<>();
        if (StringUtils.hasText(keepImageUrlsJson)) {
            for (String u : parse(keepImageUrlsJson, mapper)) {
                if (prevSet.contains(u) && !kept.contains(u)) {
                    kept.add(u);
                }
            }
        } else if (!clearAll && uploads.isEmpty()) {
            // 구 클라이언트: keep/업로드 없으면 기존 유지
            kept.addAll(prev);
        }

        List<String> next = new ArrayList<>(kept);
        for (MultipartFile file : uploads) {
            next.add(uploadFn.save(file));
        }
        if (next.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("사진은 최대 " + MAX_IMAGES + "장까지 등록할 수 있습니다.");
        }

        LinkedHashSet<String> nextSet = new LinkedHashSet<>(next);
        List<String> removed = prev.stream().filter(u -> !nextSet.contains(u)).toList();
        return new Result(List.copyOf(next), removed);
    }

    @FunctionalInterface
    public interface UploadFn {
        String save(MultipartFile file) throws IOException;
    }

    public record Result(List<String> urls, List<String> removed) {}
}

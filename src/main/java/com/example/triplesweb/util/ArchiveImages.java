package com.example.triplesweb.util;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.SavedMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 아카이브 사진 게시글의 다중 이미지 URL 목록.
 * {@code file.url}은 대표 사진, {@code images_json}이 정본이다.
 */
public final class ArchiveImages {

    public static final int MAX_IMAGES = 30;

    /** imageOrderJson에서 신규 업로드 칸을 나타내는 빈 문자열. */
    public static final String NEW_SLOT = "";

    private ArchiveImages() {}

    public static List<String> resolve(Archive archive, ObjectMapper mapper) {
        if (archive == null) return List.of();
        String cover = archive.getFile() == null ? null : archive.getFile().getUrl();
        return ChemistryImages.resolve(archive.getImagesJson(), cover, mapper);
    }

    public static List<String> resolve(String imagesJson, SavedMeta file, ObjectMapper mapper) {
        String cover = file == null ? null : file.getUrl();
        return ChemistryImages.resolve(imagesJson, cover, mapper);
    }

    public static String write(List<String> urls, ObjectMapper mapper) {
        return ChemistryImages.write(urls, mapper);
    }

    public static String primary(List<String> urls) {
        return ChemistryImages.primary(urls);
    }

    public static ChemistryImages.Result merge(
            List<String> previous,
            String keepImageUrlsJson,
            List<MultipartFile> uploads,
            ObjectMapper mapper,
            ChemistryImages.UploadFn uploadFn
    ) throws IOException {
        return merge(previous, keepImageUrlsJson, null, uploads, mapper, uploadFn);
    }

    /**
     * {@code imageOrderJson}이 있으면 그 순서로 최종 목록을 만든다.
     * 빈 문자열 칸은 {@code uploads}를 등장 순서대로 채운다.
     */
    public static ChemistryImages.Result merge(
            List<String> previous,
            String keepImageUrlsJson,
            String imageOrderJson,
            List<MultipartFile> uploads,
            ObjectMapper mapper,
            ChemistryImages.UploadFn uploadFn
    ) throws IOException {
        if (!StringUtils.hasText(imageOrderJson)) {
            return ChemistryImages.merge(previous, keepImageUrlsJson, uploads, false, mapper, uploadFn, MAX_IMAGES);
        }
        List<String> prev = previous != null ? previous : List.of();
        LinkedHashSet<String> prevSet = new LinkedHashSet<>(prev);
        List<String> tokens;
        try {
            tokens = mapper.readValue(imageOrderJson, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 순서 형식이 올바르지 않습니다.");
        }
        if (tokens == null) tokens = List.of();

        List<String> savedUploads = new ArrayList<>();
        if (uploads != null) {
            for (MultipartFile file : uploads) {
                if (file != null && !file.isEmpty()) {
                    savedUploads.add(uploadFn.save(file));
                }
            }
        }
        int uploadIndex = 0;
        List<String> next = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                if (uploadIndex >= savedUploads.size()) {
                    throw new IllegalArgumentException("이미지 순서와 업로드 파일 수가 맞지 않습니다.");
                }
                next.add(savedUploads.get(uploadIndex++));
                continue;
            }
            String url = token.trim();
            if (prevSet.contains(url) && !next.contains(url)) {
                next.add(url);
            }
        }
        while (uploadIndex < savedUploads.size()) {
            next.add(savedUploads.get(uploadIndex++));
        }
        if (next.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("사진은 최대 " + MAX_IMAGES + "장까지 등록할 수 있습니다.");
        }
        LinkedHashSet<String> nextSet = new LinkedHashSet<>(next);
        List<String> removed = prev.stream().filter(url -> !nextSet.contains(url)).toList();
        return new ChemistryImages.Result(List.copyOf(next), removed);
    }
}

package com.example.triplesweb.util;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.SavedMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 아카이브 사진 게시글의 다중 이미지 URL 목록.
 * {@code file.url}은 표지(첫 장), {@code images_json}이 정본이다.
 */
public final class ArchiveImages {

    public static final int MAX_IMAGES = 30;

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
        return ChemistryImages.merge(previous, keepImageUrlsJson, uploads, false, mapper, uploadFn, MAX_IMAGES);
    }
}

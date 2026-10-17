package com.example.triplesweb.config;

import com.example.triplesweb.entity.MediaType;
import org.springframework.core.convert.converter.Converter;

import java.util.Locale;

/**
 * 요청 경로의 미디어 타입 문자열을 {@link MediaType} enum으로 변환한다(레거시 슬러그 호환 포함).
 */
public class MediaTypeConverter implements Converter<String, MediaType> {
    @Override
    public MediaType convert(String source) {
        // 슬러그 정규화: 대문자, -를 _로
        String key = source.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');

        // 과거 호환: photo → image, photo_archive → image_archive
        if (key.equals("PHOTO")) key = "IMAGE";
        if (key.equals("PHOTO_ARCHIVE")) key = "IMAGE_ARCHIVE";

        return MediaType.valueOf(key); // 존재하지 않으면 IllegalArgumentException
    }
}

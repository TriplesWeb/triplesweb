package com.example.triplesweb.util;

import org.springframework.util.StringUtils;

/**
 * 업로드 이미지가 비었을 때 쓰는 공통 플레이스홀더.
 * 케미(chemistry) UI에는 적용하지 않는다.
 */
public final class ImageUrls {
    public static final String PLACEHOLDER = "/images/image-upload-pending.png";

    private ImageUrls() {}

    public static String orPlaceholder(String url) {
        return StringUtils.hasText(url) ? url.trim() : PLACEHOLDER;
    }
}

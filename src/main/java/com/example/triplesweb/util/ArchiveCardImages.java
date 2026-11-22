package com.example.triplesweb.util;

import com.example.triplesweb.service.UploadImageResizeService;

/** 아카이브 그리드 카드용 미리보기 URL. */
public final class ArchiveCardImages {
    private ArchiveCardImages() {}

    public static String previewUrl(String url) {
        if (url == null || url.isBlank()) return "";
        String value = url.trim();
        if (!value.startsWith("/uploads/")) return value;
        if (value.contains("?w=") || value.contains("&w=")) return value;
        return value + (value.contains("?") ? "&" : "?") + "w=" + UploadImageResizeService.CARD_WIDTH;
    }
}

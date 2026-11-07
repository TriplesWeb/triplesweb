package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.util.ImageUrls;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 홈 히어로 카드용 최신 한·일 앨범 요약.
 */
@Getter
public final class HomeAlbumHero {
    /**
     * 히어로 카드는 약 2줄(≈22rem × 0.76rem)이라 이보다 길면 CSS에서 잘린다.
     * 서버에서 문장 단위로 짧게 맞춘다.
     */
    private static final int SUMMARY_MAX = 72;
    /** 앞쪽 문단 중 비교할 개수(리드·본문 소개) */
    private static final int SUMMARY_CANDIDATE_PARAS = 3;

    private final long albumId;
    private final String title;
    private final String summary;
    private final String imageUrl;
    private final String href;
    private final String ctaLabel;

    private HomeAlbumHero(
            long albumId,
            String title,
            String summary,
            String imageUrl,
            String href,
            String ctaLabel
    ) {
        this.albumId = albumId;
        this.title = title;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.href = href;
        this.ctaLabel = ctaLabel;
    }

    public static HomeAlbumHero from(Album album) {
        long id = album.getAlbumId() != null ? album.getAlbumId() : 0L;
        String title = StringUtils.hasText(album.getAlbumName()) ? album.getAlbumName().trim() : "발매 앨범";
        return new HomeAlbumHero(
                id,
                title,
                summarize(album.getAlbumInfo()),
                ImageUrls.orPlaceholder(album.getAlbumImage()),
                id > 0 ? "/triples/album/" + id : "/triples/album",
                id > 0 ? "앨범 자세히 보기" : "발매 앨범 보기"
        );
    }

    /** 적합한 앨범이 없을 때 목록으로 보내는 기본 카드. */
    public static HomeAlbumHero fallback() {
        return new HomeAlbumHero(
                0L,
                "발매 앨범",
                "트리플에스의 발매 앨범을 확인해 보세요.",
                "/uploads/images/andless.webp",
                "/triples/album",
                "발매 앨범 보기"
        );
    }

    /**
     * 앨범 소개({@code albumInfo})에서 히어로용 한 줄을 고른다.
     * 짧은 리드보다 본문 소개 문단을 우선하고, {@code <제목>} 표기는 괄호만 벗긴다.
     */
    public static String summarize(String albumInfo) {
        if (!StringUtils.hasText(albumInfo)) {
            return "트리플에스의 발매 앨범을 확인해 보세요.";
        }
        List<String> paragraphs = paragraphs(albumInfo);
        if (paragraphs.isEmpty()) {
            return "트리플에스의 발매 앨범을 확인해 보세요.";
        }
        // 짧은 홍보 리드보다 앨범 소개 본문(보통 2번째 문단)을 쓰기 위해 앞 문단 중 가장 긴 것을 고른다.
        String chosen = paragraphs.get(0);
        int limit = Math.min(SUMMARY_CANDIDATE_PARAS, paragraphs.size());
        for (int i = 1; i < limit; i++) {
            if (paragraphs.get(i).length() > chosen.length()) {
                chosen = paragraphs.get(i);
            }
        }
        return fitCardSummary(unwrapTitleMarks(chosen));
    }

    /** 2줄 카드에 맞게 첫 문장 우선, 필요 시 문장 경계에서 자른다. */
    static String fitCardSummary(String text) {
        if (!StringUtils.hasText(text)) {
            return "트리플에스의 발매 앨범을 확인해 보세요.";
        }
        String firstSentence = firstSentence(text);
        if (firstSentence.length() <= SUMMARY_MAX) {
            return firstSentence;
        }
        return truncateAtBoundary(firstSentence, SUMMARY_MAX);
    }

    static String firstSentence(String text) {
        String trimmed = text.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？') {
                // 한국어 소개는 '다.'로 끝나는 경우가 많음. 영문 이니셜(A.)은 드묾.
                if (i + 1 >= trimmed.length() || Character.isWhitespace(trimmed.charAt(i + 1))) {
                    return trimmed.substring(0, i + 1).trim();
                }
            }
        }
        return trimmed;
    }

    private static String truncateAtBoundary(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String slice = text.substring(0, max);
        int breakAt = Math.max(
                Math.max(slice.lastIndexOf(' '), slice.lastIndexOf('，')),
                Math.max(slice.lastIndexOf(','), slice.lastIndexOf('·'))
        );
        if (breakAt >= max / 2) {
            return slice.substring(0, breakAt).trim() + "…";
        }
        return slice.trim() + "…";
    }

    private static List<String> paragraphs(String albumInfo) {
        String normalized = albumInfo.replace('\r', '\n').trim();
        List<String> out = new ArrayList<>();
        for (String paragraph : normalized.split("\n\\s*\n")) {
            String collapsed = paragraph.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(collapsed)) {
                out.add(collapsed);
            }
        }
        if (out.isEmpty()) {
            String one = normalized.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(one)) {
                out.add(one);
            }
        }
        return out;
    }

    /** 소개문에 자주 쓰는 {@code <앨범명>} 표기를 읽기 좋게 평문화한다. */
    static String unwrapTitleMarks(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.replaceAll("<([^<>]+)>", "$1");
    }

}

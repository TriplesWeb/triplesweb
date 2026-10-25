package com.example.triplesweb.util;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게시글 에디터 HTML을 상세 페이지에 안전한 형태로 정리한다.
 * 삭제 버튼·래퍼 등 편집 전용 마크업을 제거하고, 미디어 URL을 상대 경로로 정규화한다.
 */
public final class ArticleHtmlCleaner {

    private static final Pattern REMOVE_BTN = Pattern.compile(
            "(?is)<(?:span|button)[^>]*class\\s*=\\s*['\"][^'\"]*remove-btn[^'\"]*['\"][^>]*>.*?</(?:span|button)>");
    private static final Pattern CONTENT_MEDIA_OPEN = Pattern.compile(
            "(?is)<div[^>]*class\\s*=\\s*['\"][^'\"]*content-media[^'\"]*['\"][^>]*>");
    private static final Pattern SRC_ATTR = Pattern.compile(
            "(?i)(\\bsrc\\s*=\\s*)(['\"])([^'\"]+)\\2");

    private ArticleHtmlCleaner() {}

    /** 텍스트와 미디어 주소만 남겨, 같은 글인지 비교할 때 편집기 마크업 차이를 무시한다. */
    public static String fingerprint(String html) {
        String cleaned = clean(html);
        String text = cleaned.replaceAll("(?is)<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher media = SRC_ATTR.matcher(cleaned);
        StringBuilder sources = new StringBuilder();
        while (media.find()) {
            if (!sources.isEmpty()) sources.append('|');
            sources.append(media.group(3));
        }
        return text + "\n" + sources;
    }

    public static boolean hasVisibleContent(String html) {
        String finger = fingerprint(html);
        int split = finger.indexOf('\n');
        String text = split < 0 ? finger : finger.substring(0, split);
        String sources = split < 0 ? "" : finger.substring(split + 1);
        return !text.isBlank() || !sources.isBlank();
    }

    public static String clean(String html) {
        if (!StringUtils.hasText(html)) return "";

        String out = REMOVE_BTN.matcher(html).replaceAll("");
        // content-media 래퍼만 벗겨 내고 안쪽 미디어/텍스트는 유지
        out = unwrapSimpleDiv(out, CONTENT_MEDIA_OPEN);
        out = normalizeUploadSrc(out);
        return out.trim();
    }

    private static String unwrapSimpleDiv(String html, Pattern openTag) {
        StringBuilder sb = new StringBuilder(html.length());
        Matcher m = openTag.matcher(html);
        int last = 0;
        while (m.find()) {
            sb.append(html, last, m.start());
            int contentStart = m.end();
            int depth = 1;
            int i = contentStart;
            while (i < html.length() && depth > 0) {
                int nextOpen = indexOfIgnoreCase(html, "<div", i);
                int nextClose = indexOfIgnoreCase(html, "</div>", i);
                if (nextClose < 0) {
                    sb.append(html, contentStart, html.length());
                    return sb.toString();
                }
                if (nextOpen >= 0 && nextOpen < nextClose) {
                    depth++;
                    i = nextOpen + 4;
                } else {
                    depth--;
                    if (depth == 0) {
                        sb.append(html, contentStart, nextClose);
                        last = nextClose + 6;
                        break;
                    }
                    i = nextClose + 6;
                }
            }
            if (depth != 0) {
                sb.append(html, contentStart, html.length());
                return sb.toString();
            }
        }
        sb.append(html, last, html.length());
        return sb.toString();
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase(), from);
    }

    private static String normalizeUploadSrc(String html) {
        Matcher m = SRC_ATTR.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String quote = m.group(2);
            String src = m.group(3).trim();
            String normalized = toRelativeUploadUrl(src);
            m.appendReplacement(sb, Matcher.quoteReplacement(prefix + quote + normalized + quote));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String toRelativeUploadUrl(String src) {
        if (!StringUtils.hasText(src)) return src;
        String s = src.trim();
        int idx = s.indexOf("/uploads/");
        if (idx >= 0) {
            return s.substring(idx);
        }
        return s;
    }
}

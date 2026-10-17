
package com.example.triplesweb.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 본문의 {@code src} 속성에서 {@code /uploads/} 경로 URL 집합을 추출한다.
 */
public final class HtmlMediaParser {

    // src=".../uploads/...."
    private static final Pattern SRC_PATTERN = Pattern.compile(
            "(?i)\\bsrc\\s*=\\s*['\\\"]([^'\\\"]*/uploads/[^'\\\"]+)['\\\"]"
    );

    private HtmlMediaParser(){}

    public static Set<String> extractUploadUrls(String html) {
        Set<String> out = new HashSet<>();
        if (html == null || html.isBlank()) return out;
        Matcher m = SRC_PATTERN.matcher(html);
        while (m.find()) {
            String url = m.group(1);
            // 상대/절대 모두 허용
            int idx = url.indexOf("/uploads/");
            if (idx >= 0) {
                out.add(url.substring(idx)); // /uploads/부터 보관
            }
        }
        return out;
    }
}

package com.example.triplesweb.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 케미 설명 HTML을 허용 태그·속성·URL만 남기도록 정제한다.
 * 이벤트 속성·중첩 비허용 태그·javascript: URL을 제거한다.
 */
public final class ChemistryHtmlCleaner {

    private static final Set<String> ALLOWED = Set.of(
            "b", "strong", "i", "em", "u", "br", "span", "a", "div", "p"
    );
    private static final Pattern STYLE_COLOR = Pattern.compile(
            "(?i)(?:^|;)\\s*color\\s*:\\s*([^;]+)");
    private static final Pattern STYLE_BG = Pattern.compile(
            "(?i)(?:^|;)\\s*background(?:-color)?\\s*:\\s*([^;]+)");
    private static final Pattern SAFE_CSS_COLOR = Pattern.compile(
            "(?i)^\\s*(#[0-9a-f]{3,8}|rgb\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*\\)"
                    + "|rgba\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*[0-9.]+\\s*\\)"
                    + "|[a-z]+)\\s*$");

    private ChemistryHtmlCleaner() {}

    public static String clean(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        walk(doc.body());
        String out = doc.body().html()
                .replaceAll("(?i)</div>\\s*<div>", "<br>")
                .replaceAll("(?i)</?div>", "")
                .replaceAll("(?i)</p>\\s*<p>", "<br>")
                .replaceAll("(?i)</?p>", "");
        return Jsoup.clean(out, "", safelist(), new Document.OutputSettings().prettyPrint(false));
    }

    private static Safelist safelist() {
        return Safelist.none()
                .addTags("b", "strong", "i", "em", "u", "br", "span", "a")
                .addAttributes("span", "style", "class")
                .addAttributes("a", "href", "target", "rel", "class")
                .addProtocols("a", "href", "http", "https");
    }

    private static void walk(Element parent) {
        int i = 0;
        while (i < parent.childNodeSize()) {
            Node child = parent.childNode(i);
            if (child instanceof TextNode) {
                i++;
                continue;
            }
            if (!(child instanceof Element el)) {
                child.remove();
                continue;
            }
            String tag = el.normalName();
            if ("font".equals(tag) || "mark".equals(tag)) {
                Element span = new Element("span");
                String color = extractColor(el);
                String background = extractBackground(el);
                if ("mark".equals(tag) && !StringUtils.hasText(background)) {
                    background = "#ffe566";
                }
                applySpanColors(span, color, background);
                moveChildren(el, span);
                el.replaceWith(span);
                continue;
            }
            if (!ALLOWED.contains(tag)) {
                List<Node> moved = new ArrayList<>(el.childNodes());
                for (Node n : moved) {
                    el.before(n);
                }
                el.remove();
                continue;
            }
            if ("a".equals(tag)) {
                String href = el.hasAttr("href") ? el.attr("href") : "";
                if (!isSafeHttpUrl(href)) {
                    List<Node> moved = new ArrayList<>(el.childNodes());
                    for (Node n : moved) {
                        el.before(n);
                    }
                    el.remove();
                    continue;
                }
                el.clearAttributes();
                el.attr("href", href.trim());
                el.attr("target", "_blank");
                el.attr("rel", "noopener");
                el.attr("class", "chem-highlight");
            } else if ("span".equals(tag)) {
                String color = extractColor(el);
                String background = extractBackground(el);
                el.clearAttributes();
                applySpanColors(el, color, background);
            } else {
                el.clearAttributes();
            }
            walk(el);
            i++;
        }
    }

    private static void moveChildren(Element from, Element to) {
        List<Node> moved = new ArrayList<>(from.childNodes());
        for (Node n : moved) {
            to.appendChild(n);
        }
    }

    private static String extractColor(Element el) {
        String fromAttr = el.attr("color");
        if (isSafeColor(fromAttr)) {
            return fromAttr.trim();
        }
        return matchStyle(el.attr("style"), STYLE_COLOR);
    }

    private static String extractBackground(Element el) {
        String fromAttr = el.attr("bgcolor");
        if (isSafeColor(fromAttr)) {
            return fromAttr.trim();
        }
        return matchStyle(el.attr("style"), STYLE_BG);
    }

    private static String matchStyle(String style, Pattern pattern) {
        if (!StringUtils.hasText(style)) {
            return "";
        }
        Matcher m = pattern.matcher(style);
        if (m.find()) {
            String value = m.group(1).trim();
            if (isSafeColor(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isSafeColor(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("url(") || lower.contains("expression") || lower.contains("javascript")) {
            return false;
        }
        return SAFE_CSS_COLOR.matcher(value).matches();
    }

    private static void applySpanColors(Element span, String color, String background) {
        StringBuilder style = new StringBuilder();
        if (StringUtils.hasText(color)) {
            style.append("color:").append(color.trim()).append(';');
        }
        if (StringUtils.hasText(background)) {
            style.append("background-color:").append(background.trim()).append(';');
        }
        if (!style.isEmpty()) {
            span.attr("style", style.toString());
        }
    }

    private static boolean isSafeHttpUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return false;
        }
        String lower = href.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return false;
        }
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}

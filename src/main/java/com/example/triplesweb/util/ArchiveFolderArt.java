package com.example.triplesweb.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 아카이브 홈의 내장 폴더 그림과 {@code uploads/미디어 폴더}의 그림을 찾는다.
 */
@Component
public class ArchiveFolderArt {
    static final String DIR_NAME = "미디어 폴더";
    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".webp", ".svg");
    private static final int BUNDLED_MEMBER_FOLDER_COUNT = 24;
    private static final Map<String, Integer> UNIT_IMAGES = Map.ofEntries(
            Map.entry("acid angel from asia", 27),
            Map.entry("acid angel of asia", 27),
            Map.entry("+(kr)ystal eyes", 28),
            Map.entry("acid eyes", 29),
            Map.entry("lovelution", 30),
            Map.entry("evolution", 31),
            Map.entry("nxt", 32),
            Map.entry("glow", 33),
            Map.entry("aria", 34),
            Map.entry("visionary vision", 35),
            Map.entry("∞!", 36),
            Map.entry("alphie", 37),
            Map.entry("msnz moon", 38),
            Map.entry("msnz sun", 39),
            Map.entry("msnz neptune", 40),
            Map.entry("msnz zenith", 41),
            Map.entry("aura", 42)
    );

    private final Path uploadRoot;

    public ArchiveFolderArt(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir);
    }

    public static String bundledIcon(ArchiveFolderClassifier.Kind kind, String key, String label) {
        return switch (kind) {
            case SOLO -> memberIcon(key);
            case UNIT -> unitIcon(label);
            default -> "";
        };
    }

    private static String memberIcon(String key) {
        int number = MemberPortraitOptions.memberIndex(key) + 1;
        return number <= BUNDLED_MEMBER_FOLDER_COUNT ? "/images/archive/member-folders/" + number + ".png" : "";
    }

    private static String unitIcon(String label) {
        if (label == null) return "";
        Integer number = UNIT_IMAGES.get(label.strip().toLowerCase(Locale.ROOT));
        return number == null ? "" : "/images/archive/unit-folders/" + number + ".png";
    }

    public String urlFor(ArchiveFolderClassifier.Kind kind, String key, String label) {
        Path dir = uploadRoot.resolve(DIR_NAME);
        if (!Files.isDirectory(dir)) return "";
        for (String name : names(kind, key, label)) {
            String found = firstFile(dir, name);
            if (!found.isBlank()) return publicUrl(found);
        }
        return "";
    }

    static List<String> names(ArchiveFolderClassifier.Kind kind, String key, String label) {
        List<String> names = new ArrayList<>();
        if (key != null && !key.isBlank()) names.add(key);
        if (label != null && !label.isBlank()) names.add(label.trim());
        switch (kind) {
            case GROUP -> names.addAll(List.of("S", "s", "group", "단체", "전체 멤버", "전체멤버"));
            case UNIT -> names.addAll(List.of("unit", "유닛"));
            case COMBO -> {
                Integer size = ArchiveFolderClassifier.comboSize(key);
                if (size != null) {
                    names.add(ArchiveFolderClassifier.comboLabel(size));
                    names.add(size + "인");
                    names.add(size + "명 케미");
                    if (size >= 5) names.addAll(List.of("6명 이상", "6인"));
                }
            }
            case SOLO -> {
                if (key != null && !key.isBlank()) names.add(key.toUpperCase(Locale.ROOT));
                if (label != null && label.contains(" ")) {
                    names.add(label.substring(label.indexOf(' ') + 1).trim());
                }
                names.addAll(List.of("member", "solo", "멤버"));
            }
            case OTHER -> {
                return List.of();
            }
        }
        return names.stream().filter(name -> name != null && !name.isBlank()).distinct().toList();
    }

    private static String firstFile(Path dir, String stem) {
        if (stem == null || stem.isBlank()) return "";
        for (String ext : EXTENSIONS) {
            Path file = dir.resolve(stem + ext);
            if (Files.isRegularFile(file)) return file.getFileName().toString();
        }
        String needle = stem.toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> matchesStem(stripExt(name), needle))
                    .findFirst()
                    .orElse("");
        } catch (IOException ignored) {
            return "";
        }
    }

    private static boolean matchesStem(String base, String needle) {
        String value = base.toLowerCase(Locale.ROOT);
        return value.equals(needle)
                || value.startsWith(needle + " ")
                || value.startsWith(needle + "_")
                || value.startsWith(needle + "-")
                || value.endsWith(" " + needle);
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String publicUrl(String filename) {
        return "/uploads/" + encode(DIR_NAME) + "/" + encode(filename);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

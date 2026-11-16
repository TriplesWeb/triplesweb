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
import java.util.stream.Stream;

/**
 * {@code uploads/미디어 폴더}에 있는 폴더 그림을 아카이브 홈 아이콘으로 쓴다.
 */
@Component
public class ArchiveFolderArt {
    static final String DIR_NAME = "미디어 폴더";
    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".webp", ".svg");

    private final Path uploadRoot;

    public ArchiveFolderArt(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir);
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
                    names.add(size + "명");
                    names.add(String.valueOf(size));
                }
                names.add("combo");
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

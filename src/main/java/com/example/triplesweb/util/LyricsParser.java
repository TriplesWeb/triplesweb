package com.example.triplesweb.util;

import com.example.triplesweb.entity.Artist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsParser {

    private static final String ALL = "ALL";
    private static final String DEFAULT_COLOR = "#e3e2e8";
    private static final Pattern PREFIX = Pattern.compile("^([^:：]*?)[:：]");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private LyricsParser() {}

    public static Result parse(String lyrics, List<Artist> artists) {
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put(ALL, new Part(ALL, null));
        artists.stream()
                .sorted(Comparator.comparing(Artist::getArtistId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(artist -> parts.putIfAbsent(artist.getName(),
                        new Part(artist.getName(), validColor(artist.getColor()))));

        List<List<Line>> stanzas = new ArrayList<>();
        List<Line> stanza = new ArrayList<>();
        List<String> currentParts = List.of();
        Set<String> usedParts = new HashSet<>();
        for (String rawLine : lyrics.split("\\R")) {
            if (rawLine.isBlank()) {
                if (!stanza.isEmpty()) {
                    stanzas.add(stanza);
                    stanza = new ArrayList<>();
                }
                continue;
            }

            String text = rawLine;
            Matcher prefix = PREFIX.matcher(rawLine);
            if (prefix.find()) {
                List<String> names = Arrays.stream(prefix.group(1).split(",", -1))
                        .map(String::strip)
                        .distinct()
                        .toList();
                if (names.stream().allMatch(parts::containsKey)) {
                    currentParts = names;
                    text = rawLine.substring(prefix.end()).stripLeading();
                }
            }
            if (!text.isBlank()) {
                usedParts.addAll(currentParts);
                stanza.add(makeLine(text, currentParts, parts));
            }
        }
        if (!stanza.isEmpty()) {
            stanzas.add(stanza);
        }
        List<Part> legend = parts.values().stream()
                .filter(part -> usedParts.contains(part.name()))
                .toList();
        return new Result(stanzas, legend);
    }

    private static Line makeLine(String text, List<String> names, Map<String, Part> parts) {
        List<String> colors = names.stream()
                .filter(name -> !ALL.equals(name))
                .map(name -> parts.get(name).color())
                .toList();
        if (colors.size() > 1) {
            String gradient = "linear-gradient(90deg, " + String.join(", ", colors) + ")";
            return new Line(text, null, gradient);
        }
        return new Line(text, colors.isEmpty() ? null : colors.get(0), null);
    }

    private static String validColor(String color) {
        return color != null && HEX_COLOR.matcher(color).matches() ? color : DEFAULT_COLOR;
    }

    public record Result(List<List<Line>> stanzas, List<Part> legend) {
        public boolean hasStanzas() {
            return !stanzas.isEmpty();
        }

        public boolean hasLegend() {
            return !legend.isEmpty();
        }
    }

    public record Line(String text, String color, String gradient) {}

    public record Part(String name, String color) {}
}

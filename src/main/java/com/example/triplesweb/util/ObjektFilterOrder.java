package com.example.triplesweb.util;

import java.util.*;
import java.util.regex.Pattern;

/** Shared picker ordering and one Korean-name option per solo member. */
public final class ObjektFilterOrder {
    private static final List<String> SEASON_FAMILIES = List.of("atom", "binary", "cream", "divine", "ever");
    private static final Pattern SEASON = Pattern.compile("([a-z]+)([0-9]+)");
    private static final Comparator<String> ALPHABETICAL = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private ObjektFilterOrder() {}

    public static Comparator<String> members() {
        return Comparator.comparingInt(MemberPortraitOptions::memberIndex).thenComparing(ALPHABETICAL);
    }

    public static Comparator<String> seasons() {
        return Comparator.<String>comparingLong(ObjektFilterOrder::seasonIndex).reversed().thenComparing(ALPHABETICAL);
    }

    public static List<String> sortedMembers(Collection<String> values) {
        return sorted(values.stream().map(MemberPortraitOptions::memberName).toList(), members());
    }
    public static List<String> sortedSeasons(Collection<String> values) { return sorted(values, seasons()); }

    private static List<String> sorted(Collection<String> values, Comparator<String> comparator) {
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank())
                .distinct().sorted(comparator).toList();
    }

    private static long seasonIndex(String value) {
        var match = SEASON.matcher(value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", ""));
        if (!match.matches()) return -1;
        int family = SEASON_FAMILIES.indexOf(match.group(1));
        if (family < 0) return -1;
        try {
            long generation = Long.parseLong(match.group(2));
            return Math.addExact(Math.multiplyExact(generation, SEASON_FAMILIES.size()), family);
        } catch (ArithmeticException | NumberFormatException ignored) { return -1; }
    }
}

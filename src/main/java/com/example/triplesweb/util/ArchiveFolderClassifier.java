package com.example.triplesweb.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 아카이브 출연자 문자열을 S / 유닛 / 2인 이상 / 솔로 폴더로 나눈다.
 * 한 자료는 한 폴더에만 들어간다.
 */
public final class ArchiveFolderClassifier {
    public enum Kind { GROUP, UNIT, COMBO, SOLO, OTHER }

    public record UnitMembers(long unitId, String unitName, Set<String> members) {}

    public record Match(Kind kind, String key, List<String> members, Long unitId, String label) {}

    private ArchiveFolderClassifier() {
    }

    public static List<String> membersOf(String artistNames) {
        return ObjektFilterOrder.sortedMembers(MemberPortraitOptions.objektMembers(artistNames));
    }

    public static Match classify(String artistNames, List<UnitMembers> units) {
        List<String> members = membersOf(artistNames);
        Set<String> unique = new LinkedHashSet<>(members);
        if (unique.size() == 24 && unique.containsAll(MemberPortraitOptions.koreanOrder())) {
            return new Match(Kind.GROUP, "s", List.copyOf(unique), null, "S");
        }
        if (unique.size() == 1) {
            String name = unique.iterator().next();
            int index = MemberPortraitOptions.memberIndex(name);
            if (index < 24) {
                String korean = MemberPortraitOptions.koreanOrder().get(index);
                return new Match(Kind.SOLO, "s" + (index + 1), List.of(korean), null, "S" + (index + 1) + " " + korean);
            }
        }
        if (units != null && unique.size() >= 2) {
            for (UnitMembers unit : units) {
                if (unit == null || unit.members() == null || unit.members().isEmpty()) continue;
                if (unit.members().equals(unique)) {
                    String name = unit.unitName() == null || unit.unitName().isBlank() ? "유닛" : unit.unitName();
                    return new Match(Kind.UNIT, "unit-" + unit.unitId(), List.copyOf(unique), unit.unitId(), name);
                }
            }
        }
        if (unique.size() >= 2) {
            List<String> ordered = List.copyOf(unique);
            return new Match(Kind.COMBO, "combo", ordered, null, String.join(" × ", ordered));
        }
        return new Match(Kind.OTHER, "other", List.copyOf(unique), null, "기타");
    }

    public static boolean matches(Match actual, String folder, List<String> comboMembers) {
        if (actual == null || folder == null || folder.isBlank()) return false;
        String key = folder.trim().toLowerCase(Locale.ROOT);
        return switch (actual.kind()) {
            case GROUP -> key.equals("s");
            case SOLO, UNIT -> key.equals(actual.key());
            case COMBO -> key.equals("combo") && actual.members().equals(membersOf(String.join(",",
                    comboMembers == null ? List.of() : comboMembers)));
            case OTHER -> false;
        };
    }
}

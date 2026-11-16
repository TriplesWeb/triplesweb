package com.example.triplesweb.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 아카이브 출연자 문자열을 전체 멤버 / 유닛 / 2·3·4명 케미 / 5명 이상 / 솔로 폴더로 나눈다.
 * 각 사진은 인원 수에 맞는 폴더 하나에만 들어간다.
 */
public final class ArchiveFolderClassifier {
    public enum Kind { GROUP, UNIT, COMBO, SOLO, OTHER }

    public static final int[] COMBO_FOLDERS = {2, 3, 4, 5};
    private static final Pattern COMBO_FOLDER = Pattern.compile("(?i)combo-(\\d+)");

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
            return new Match(Kind.GROUP, "s", List.copyOf(unique), null, "전체 멤버");
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
            int min = tightestComboMin(unique.size());
            return new Match(Kind.COMBO, "combo-" + min, List.copyOf(unique), null, comboLabel(min));
        }
        return new Match(Kind.OTHER, "other", List.copyOf(unique), null, "기타");
    }

    public static String comboLabel(int min) {
        if (min <= 2) return "2명 케미";
        if (min == 3) return "3명 케미";
        if (min == 4) return "4명 케미";
        return "5명 이상";
    }

    public static boolean comboFitsFolder(int memberCount, int folderSize) {
        int bucket = folderSize >= 5 ? 5 : Math.max(2, folderSize);
        return tightestComboMin(memberCount) == bucket;
    }

    public static String canonicalComboFolder(String folder) {
        if (folder != null && folder.equalsIgnoreCase("combo-6")) return "combo-5";
        return folder;
    }

    public static int tightestComboMin(int memberCount) {
        int chosen = COMBO_FOLDERS[0];
        for (int min : COMBO_FOLDERS) {
            if (memberCount >= min) chosen = min;
        }
        return chosen;
    }

    public static Integer comboSize(String folder) {
        if (folder == null) return null;
        Matcher matcher = COMBO_FOLDER.matcher(folder.trim());
        if (!matcher.matches()) return null;
        int size = Integer.parseInt(matcher.group(1));
        if (size < 2 || size > 23) return null;
        return size;
    }

    public static boolean matches(Match actual, String folder, List<String> comboMembers) {
        if (actual == null || folder == null || folder.isBlank()) return false;
        String key = folder.trim().toLowerCase(Locale.ROOT);
        return switch (actual.kind()) {
            case GROUP -> key.equals("s");
            case SOLO, UNIT -> key.equals(actual.key());
            case COMBO -> matchesCombo(actual, key, comboMembers);
            case OTHER -> false;
        };
    }

    private static boolean matchesCombo(Match actual, String key, List<String> comboMembers) {
        List<String> wanted = membersOf(String.join(",", comboMembers == null ? List.of() : comboMembers));
        Integer min = comboSize(key);
        if (min != null) {
            if (!wanted.isEmpty() && !actual.members().equals(wanted)) return false;
            return comboFitsFolder(actual.members().size(), min);
        }
        if (!key.equals("combo")) return false;
        if (wanted.isEmpty()) return false;
        return actual.members().equals(wanted);
    }
}

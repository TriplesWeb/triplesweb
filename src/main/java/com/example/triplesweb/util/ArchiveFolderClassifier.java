package com.example.triplesweb.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 아카이브 출연자 문자열을 전체 멤버 / 유닛 / 2·3·4명 케미 / 5명 이상 / 솔로로 나눈다.
 * 전체 멤버 폴더({@code s})는 멤버를 고르지 않으면 등록된 자료를 모두 포함한다.
 * 고른 멤버가 유닛 구성과 같으면 그 유닛에 지정된 자료를, 그 외에는 고른 멤버가 모두 나온 자료를 포함한다.
 */
public final class ArchiveFolderClassifier {
    public enum Kind { GROUP, UNIT, COMBO, SOLO, OTHER }

    public static final int[] COMBO_FOLDERS = {2, 3, 4, 5};
    private static final Pattern COMBO_FOLDER = Pattern.compile("(?i)combo-(\\d+)");

    public record UnitMembers(long unitId, String unitName, Set<String> members, String formationDate) {
        public UnitMembers(long unitId, String unitName, Set<String> members) {
            this(unitId, unitName, members, null);
        }
    }

    public record Match(Kind kind, String key, List<String> members, Set<Long> unitIds, String label) {}

    private ArchiveFolderClassifier() {
    }

    public static List<String> membersOf(String artistNames) {
        return ObjektFilterOrder.sortedMembers(MemberPortraitOptions.objektMembers(artistNames));
    }

    public static int memberMask(String artistNames) {
        int mask = 0;
        for (String member : MemberPortraitOptions.objektMembers(artistNames)) {
            mask |= 1 << MemberPortraitOptions.memberIndex(member);
        }
        return mask;
    }

    public static int memberRank(int mask) {
        return Math.min(24, Integer.numberOfTrailingZeros(mask));
    }

    public static Match classify(String artistNames, List<UnitMembers> units) {
        return classify(artistNames, units, Set.of());
    }

    /**
     * @param assignedUnitIds 저장된 소속 유닛. 보이는 소속 유닛이 있으면 케미 분류에서 제외한다.
     */
    public static Match classify(String artistNames, List<UnitMembers> units, Set<Long> assignedUnitIds) {
        List<String> members = membersOf(artistNames);
        Set<String> unique = new LinkedHashSet<>(members);
        Set<Long> unitIds = Set.copyOf(assignedUnitIds);
        if (unique.size() == 24 && unique.containsAll(MemberPortraitOptions.koreanOrder())) {
            return new Match(Kind.GROUP, "s", members, unitIds, "전체 멤버");
        }
        if (unique.size() == 1) {
            String name = unique.iterator().next();
            int index = MemberPortraitOptions.memberIndex(name);
            if (index < 24) {
                String korean = MemberPortraitOptions.koreanOrder().get(index);
                return new Match(Kind.SOLO, "s" + (index + 1), List.of(korean), unitIds, "S" + (index + 1) + " " + korean);
            }
        }
        if (!unitIds.isEmpty() && units != null) {
            for (UnitMembers unit : units) {
                if (unit == null || !unitIds.contains(unit.unitId())) continue;
                String name = unit.unitName() == null || unit.unitName().isBlank() ? "유닛" : unit.unitName();
                return new Match(Kind.UNIT, "unit-" + unit.unitId(), members, unitIds, name);
            }
        }
        if (unique.size() >= 2) {
            int min = tightestComboMin(unique.size());
            return new Match(Kind.COMBO, "combo-" + min, members, unitIds, comboLabel(min));
        }
        return new Match(Kind.OTHER, "other", members, unitIds, "기타");
    }

    /** 사진일이 결성일 당일 이후면 true. 날짜가 없으면(미지정) 허용. */
    public static boolean isFormedBy(UnitMembers unit, LocalDate registeredOn) {
        if (unit == null || registeredOn == null) return true;
        LocalDate formed = parseFormationDate(unit.formationDate());
        if (formed == null) return true;
        return !registeredOn.isBefore(formed);
    }

    static LocalDate parseFormationDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
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
        try {
            int size = Integer.parseInt(matcher.group(1));
            return size >= 2 && size <= 23 ? size : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean matches(Match actual, String folder, List<String> comboMembers) {
        return matches(actual, folder, comboMembers, List.of());
    }

    public static boolean matches(Match actual, String folder, List<String> comboMembers, List<UnitMembers> units) {
        if (actual == null || folder == null || folder.isBlank()) return false;
        String key = folder.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("unit-")) {
            return actual.unitIds().stream().anyMatch(unitId -> key.equals("unit-" + unitId));
        }
        if (key.equals("s")) {
            List<String> wanted = membersOf(String.join(",", comboMembers == null ? List.of() : comboMembers));
            if (wanted.isEmpty()) return true;
            Set<String> wantedSet = new LinkedHashSet<>(wanted);
            Set<String> actualSet = new LinkedHashSet<>(actual.members());
            // 유닛 구성 멤버 폴더는 저장된 유닛 소속을 사용한다.
            if (isExactUnitMemberSet(wantedSet, units)) {
                for (UnitMembers unit : units) {
                    if (unit != null && unit.members() != null && unit.members().equals(wantedSet)) {
                        return actual.unitIds().contains(unit.unitId());
                    }
                }
            }
            return actualSet.containsAll(wantedSet);
        }
        return switch (actual.kind()) {
            case GROUP -> key.equals("s");
            case SOLO -> key.equals(actual.key());
            case COMBO -> matchesCombo(actual, key, comboMembers);
            case UNIT, OTHER -> false;
        };
    }

    /** 선택 멤버 집합이 등록된 유닛 구성과 완전히 같은지. */
    public static boolean isExactUnitMemberSet(Set<String> members, List<UnitMembers> units) {
        if (members == null || members.size() < 2 || units == null || units.isEmpty()) return false;
        for (UnitMembers unit : units) {
            if (unit == null || unit.members() == null || unit.members().isEmpty()) continue;
            if (unit.members().equals(members)) return true;
        }
        return false;
    }

    /**
     * 출연진이 유닛 구성과 완전히 같고 결성일 이후일 때 그 유닛 id.
     * unit_id 백필·이관용. 평소 분류는 {@link #classify}의 저장 unitId만 본다.
     */
    public static Long unitIdForExactCast(String artistNames, LocalDate registeredOn, List<UnitMembers> units) {
        if (units == null || units.isEmpty()) return null;
        Set<String> cast = new LinkedHashSet<>(membersOf(artistNames));
        if (cast.size() < 2) return null;
        for (UnitMembers unit : units) {
            if (unit == null || unit.members() == null || unit.members().size() < 2) continue;
            if (!unit.members().equals(cast)) continue;
            if (!isFormedBy(unit, registeredOn)) continue;
            return unit.unitId();
        }
        return null;
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

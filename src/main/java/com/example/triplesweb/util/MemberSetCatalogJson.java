package com.example.triplesweb.util;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 멤버 선택 요약용 케미·유닛 JSON과 토글 라벨.
 * 프론트 {@code member-selection-summary.js}와 같은 우선순위를 쓴다.
 */
public final class MemberSetCatalogJson {
    public static final String EMPTY_LABEL = "멤버 선택";
    public static final String GROUP_LABEL = "단체 콘텐츠";

    public record NamedSet(String name, List<String> members) {}

    private MemberSetCatalogJson() {
    }

    public static String chemistry(List<ChemistryDto> pairs, List<ChemistryGroupDto> groups) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        Set<String> seen = new LinkedHashSet<>();
        if (pairs != null) {
            for (ChemistryDto item : pairs) {
                if (item == null) continue;
                String rawMembers = (item.getMemberA() == null ? "" : item.getMemberA())
                        + "," + (item.getMemberB() == null ? "" : item.getMemberB());
                first = appendChemistry(json, first, seen, ArchiveFolderClassifier.membersOf(rawMembers),
                        item.getLabel(), "pair", item.getId());
            }
        }
        if (groups != null) {
            for (ChemistryGroupDto item : groups) {
                if (item == null) continue;
                List<String> members = item.getMembers() == null ? List.of() : item.getMembers();
                first = appendChemistry(json, first, seen,
                        ArchiveFolderClassifier.membersOf(String.join(",", members)),
                        item.getLabel(), "group", item.getId());
            }
        }
        return json.append(']').toString();
    }

    public static String units(List<Map<String, Object>> checkboxOptions) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        if (checkboxOptions != null) {
            for (Map<String, Object> option : checkboxOptions) {
                if (option == null) continue;
                Object rawName = option.get("unitName");
                String name = rawName == null ? "" : String.valueOf(rawName).trim();
                Object rawMembers = option.get("membersCsv");
                List<String> members = ArchiveFolderClassifier.membersOf(rawMembers == null ? "" : String.valueOf(rawMembers));
                if (name.isEmpty() || members.size() < 2) continue;
                if (!first) json.append(',');
                first = false;
                json.append("{\"name\":").append(jsonString(name));
                Object unitId = option.get("unitId");
                if (unitId instanceof Number number) json.append(",\"unitId\":").append(number.longValue());
                json.append(",\"members\":[");
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) json.append(',');
                    json.append(jsonString(members.get(i)));
                }
                json.append("]}");
            }
        }
        return json.append(']').toString();
    }

    public static List<NamedSet> chemistrySets(List<ChemistryDto> pairs, List<ChemistryGroupDto> groups) {
        List<NamedSet> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (pairs != null) {
            for (ChemistryDto item : pairs) {
                if (item == null) continue;
                String rawMembers = (item.getMemberA() == null ? "" : item.getMemberA())
                        + "," + (item.getMemberB() == null ? "" : item.getMemberB());
                addSet(out, seen, ArchiveFolderClassifier.membersOf(rawMembers), displayLabel(item.getLabel()));
            }
        }
        if (groups != null) {
            for (ChemistryGroupDto item : groups) {
                if (item == null) continue;
                List<String> members = item.getMembers() == null ? List.of() : item.getMembers();
                addSet(out, seen, ArchiveFolderClassifier.membersOf(String.join(",", members)), displayLabel(item.getLabel()));
            }
        }
        return out;
    }

    public static List<NamedSet> unitSets(List<Map<String, Object>> checkboxOptions) {
        List<NamedSet> out = new ArrayList<>();
        if (checkboxOptions == null) return out;
        for (Map<String, Object> option : checkboxOptions) {
            if (option == null) continue;
            Object rawName = option.get("unitName");
            String name = rawName == null ? "" : String.valueOf(rawName).trim();
            Object rawMembers = option.get("membersCsv");
            List<String> members = ArchiveFolderClassifier.membersOf(rawMembers == null ? "" : String.valueOf(rawMembers));
            if (name.isEmpty() || members.size() < 2) continue;
            out.add(new NamedSet(name, members));
        }
        return out;
    }

    public static String toggleLabel(Collection<String> selected, List<NamedSet> chems, List<NamedSet> units) {
        List<String> names = selected == null ? List.of() : selected.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (names.isEmpty()) return EMPTY_LABEL;
        if (names.size() == 1) return names.get(0);
        NamedSet chem = exact(names, chems);
        if (chem != null) return chem.name();
        NamedSet unit = exact(names, units);
        if (unit != null) return unit.name();
        if (MemberPortraitOptions.coversFullLineup(names)) return GROUP_LABEL;
        return names.size() + "명";
    }

    private static void addSet(List<NamedSet> out, Set<String> seen, List<String> members, String label) {
        if (members == null || members.size() < 2 || label == null || label.isBlank()) return;
        if (!seen.add(String.join("\u0000", members))) return;
        out.add(new NamedSet(label, members));
    }

    private static NamedSet exact(List<String> selected, List<NamedSet> catalog) {
        if (catalog == null) return null;
        for (NamedSet entry : catalog) {
            if (entry == null || entry.members() == null || entry.name() == null || entry.name().isBlank()) continue;
            if (sameMemberSet(selected, entry.members())) return entry;
        }
        return null;
    }

    private static boolean sameMemberSet(List<String> left, List<String> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) return false;
        Set<String> rightKeys = new LinkedHashSet<>();
        for (String name : right) rightKeys.add(memberKey(name));
        if (rightKeys.size() != left.size()) return false;
        for (String name : left) {
            if (!rightKeys.contains(memberKey(name))) return false;
        }
        return true;
    }

    private static String memberKey(String name) {
        return String.valueOf(name == null ? "" : name).trim().toLowerCase().replaceAll("\\s+", "");
    }

    private static boolean appendChemistry(StringBuilder json, boolean first, Set<String> seen,
                                           List<String> members, String rawLabel, String type, Long id) {
        if (members == null || members.size() < 2) return first;
        String label = displayLabel(rawLabel);
        if (label.isEmpty()) return first;
        if (!seen.add(String.join("\u0000", members))) return first;
        if (!first) json.append(',');
        json.append("{\"members\":[");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonString(members.get(i)));
        }
        json.append("],\"label\":").append(jsonString(label));
        if (id != null) {
            json.append(",\"href\":").append(jsonString("/triples/chemistry?" + type + "=" + id));
        }
        json.append('}');
        return false;
    }

    static String displayLabel(String label) {
        if (label == null || label.isBlank()) return "";
        List<String> parts = new ArrayList<>();
        for (String line : label.split("\\R+")) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (!trimmed.isEmpty()) parts.add(trimmed);
        }
        return String.join(" · ", parts);
    }

    static String jsonString(String value) {
        String raw = value == null ? "" : value;
        StringBuilder out = new StringBuilder(raw.length() + 2);
        out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '<' -> out.append("\\u003c");
                default -> out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}

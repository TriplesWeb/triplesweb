package com.example.triplesweb.util;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.entity.ChemistryLabelTone;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 케미 24×24 표를 HTML로 만들어 첫 페인트에 포함한다.
 * 프론트 {@code chemistry.js} buildTable 출력과 맞춘다.
 */
public final class ChemistryMatrixHtml {

    private static final String BASE = "/uploads/chemistry";

    private ChemistryMatrixHtml() {}

    public static String thead(boolean english) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<tr><th class=\"chem-corner\"><img src=\"")
                .append(BASE)
                .append("/logo.png\" alt=\"tripleS\" decoding=\"async\"></th>");
        List<String> names = TripleSMembers.NAMES_KO;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String color = colorAt(i);
            String label = memberLabel(name, english);
            String escaped = escapeHtml(label);
            sb.append("<th class=\"chem-col-head\" style=\"--member-color:")
                    .append(color)
                    .append("\"><a href=\"")
                    .append(memberHref(name, i, null))
                    .append("\" title=\"")
                    .append(escaped)
                    .append("\" class=\"chem-member-link\"><span class=\"chem-member-name\">")
                    .append(escaped)
                    .append("</span></a></th>");
        }
        sb.append("</tr>");
        return sb.toString();
    }

    public static String tbody(List<ChemistryDto> entries, boolean english) {
        List<String> names = TripleSMembers.NAMES_KO;
        int n = names.size();
        Cell[][] matrix = new Cell[n][n];
        Map<String, Long> artistIds = new HashMap<>();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = Cell.self(i + 1);
                } else {
                    matrix[i][j] = Cell.empty();
                }
            }
        }

        if (entries != null) {
            for (ChemistryDto entry : entries) {
                if (entry == null) continue;
                rememberArtistId(artistIds, entry.getMemberA(), entry.getArtistAId());
                rememberArtistId(artistIds, entry.getMemberB(), entry.getArtistBId());
                int ia = TripleSMembers.NAMES_KO.indexOf(entry.getMemberA());
                int ib = TripleSMembers.NAMES_KO.indexOf(entry.getMemberB());
                if (ia < 0 || ib < 0 || ia == ib) continue;
                String labelKo = entry.getLabel() == null ? "" : entry.getLabel().trim();
                if (!StringUtils.hasText(labelKo)) continue;
                String labelEn = entry.getLabelEn() == null ? "" : entry.getLabelEn().trim();
                String display = english
                        ? (StringUtils.hasText(labelEn) ? entry.getLabelEn() : entry.getLabel())
                        : entry.getLabel();
                ChemistryLabelTone tone = entry.getLabelTone() != null
                        ? entry.getLabelTone()
                        : ChemistryLabelTone.NONE;
                placeChem(matrix, names, ia, ib, display, tone);
                placeChem(matrix, names, ib, ia, display, tone);
            }
        }

        StringBuilder sb = new StringBuilder(Math.max(8192, n * n * 48));
        for (int ri = 0; ri < n; ri++) {
            String color = colorAt(ri);
            String rowLabel = memberLabel(names.get(ri), english);
            String escapedRow = escapeHtml(rowLabel);
            sb.append("<tr><th class=\"chem-row-head\" style=\"--member-color:")
                    .append(color)
                    .append("\"><a href=\"")
                    .append(memberHref(names.get(ri), ri, artistIds))
                    .append("\" title=\"")
                    .append(escapedRow)
                    .append("\" class=\"chem-member-link\"><span class=\"chem-member-name\">")
                    .append(escapedRow)
                    .append("</span></a></th>");
            for (int ci = 0; ci < n; ci++) {
                Cell cell = matrix[ri][ci];
                if (cell.type == CellType.SELF) {
                    sb.append("<td class=\"chem-self\"><img src=\"")
                            .append(BASE)
                            .append("/symbols/S")
                            .append(cell.s)
                            .append(".png\" alt=\"S")
                            .append(cell.s)
                            .append("\" loading=\"lazy\" decoding=\"async\"></td>");
                } else if (cell.type == CellType.CHEM) {
                    String toneCls = toneClass(cell.tone);
                    sb.append("<td class=\"chem-cell");
                    if (!toneCls.isEmpty()) {
                        sb.append(' ').append(toneCls);
                    }
                    sb.append("\" data-person=\"")
                            .append(escapeHtml(cell.person))
                            .append("\" data-key=\"")
                            .append(escapeHtml(cell.key))
                            .append("\" data-m1=\"")
                            .append(escapeHtml(names.get(ri)))
                            .append("\" data-m2=\"")
                            .append(escapeHtml(names.get(ci)))
                            .append("\" data-tone=\"")
                            .append(escapeHtml(cell.tone.name()))
                            .append("\" tabindex=\"0\" role=\"button\">")
                            .append(formatLabel(cell.label))
                            .append("</td>");
                } else {
                    sb.append("<td class=\"chem-empty\"></td>");
                }
            }
            sb.append("</tr>");
        }
        return sb.toString();
    }

    private static void placeChem(Cell[][] matrix, List<String> names, int ri, int ci,
                                  String display, ChemistryLabelTone tone) {
        String person = names.get(ri);
        String other = names.get(ci);
        matrix[ri][ci] = Cell.chem(display, person, chemKey(person, other), tone);
    }

    private static String chemKey(String a, String b) {
        return a + "과 " + b + "의 케미명";
    }

    private static void rememberArtistId(Map<String, Long> map, String name, Long id) {
        if (StringUtils.hasText(name) && id != null) {
            map.put(name, id);
        }
    }

    private static String memberHref(String name, int index, Map<String, Long> artistIds) {
        if (artistIds != null) {
            Long id = artistIds.get(name);
            if (id != null) {
                return "/triples/artist/" + id;
            }
        }
        return "/triples/artist/" + (index + 1);
    }

    private static String memberLabel(String nameKo, boolean english) {
        if (!english) {
            return nameKo;
        }
        String en = TripleSMembers.NAME_EN_BY_KO.get(nameKo);
        return en != null ? en : nameKo;
    }

    private static String colorAt(int i) {
        List<String> colors = TripleSMembers.COLORS;
        if (i >= 0 && i < colors.size()) {
            return colors.get(i);
        }
        return "#00f5d4";
    }

    private static String toneClass(ChemistryLabelTone tone) {
        if (tone == null) {
            return "";
        }
        return switch (tone) {
            case UNDECIDED -> "chem-tone-undecided";
            case RECENT -> "chem-tone-recent";
            case FAN -> "chem-tone-fan";
            default -> "";
        };
    }

    private static String formatLabel(String label) {
        return escapeHtml(label == null ? "" : label).replace("\n", "<br>");
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private enum CellType { SELF, CHEM, EMPTY }

    private static final class Cell {
        final CellType type;
        final int s;
        final String label;
        final String person;
        final String key;
        final ChemistryLabelTone tone;

        private Cell(CellType type, int s, String label, String person, String key, ChemistryLabelTone tone) {
            this.type = type;
            this.s = s;
            this.label = label;
            this.person = person;
            this.key = key;
            this.tone = tone;
        }

        static Cell self(int s) {
            return new Cell(CellType.SELF, s, null, null, null, null);
        }

        static Cell empty() {
            return new Cell(CellType.EMPTY, 0, null, null, null, null);
        }

        static Cell chem(String label, String person, String key, ChemistryLabelTone tone) {
            return new Cell(CellType.CHEM, 0, label, person, key, tone);
        }
    }
}

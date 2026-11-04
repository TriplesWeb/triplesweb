package com.example.triplesweb.navigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 허브·leaf 템플릿에 넣을 섹션 서브내비 모델. */
public final class SectionNavView {
    private SectionNavView() {}

    public static Map<String, Object> of(String sectionId, String activePath) {
        return SiteNavigation.section(sectionId)
                .map(section -> toModel(section, activePath))
                .orElseGet(Map::of);
    }

    private static Map<String, Object> toModel(SiteNavigation.Section section, String activePath) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SiteNavigation.Item item : section.items()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", item.label());
            row.put("href", item.href());
            row.put("active", isActive(activePath, item.href()));
            items.add(row);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", section.id());
        model.put("label", section.label());
        model.put("href", section.href());
        model.put("hasSubnav", !items.isEmpty());
        model.put("items", items);
        return model;
    }

    private static boolean isActive(String activePath, String href) {
        if (activePath == null || activePath.isBlank() || href == null || href.isBlank()) {
            return false;
        }
        if (activePath.equals(href)) {
            return true;
        }
        // /media/signal?tab=mv 와 /cosmo/objekt?… 등 쿼리·하위 경로
        return activePath.startsWith(href + "/") || activePath.startsWith(href + "?");
    }
}

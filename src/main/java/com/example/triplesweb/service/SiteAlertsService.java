package com.example.triplesweb.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 헤더 종 패널용. 세션 없이 오늘 출시된 오브젝트 드롭을 돌려 준다. */
@Service
@RequiredArgsConstructor
public class SiteAlertsService {

    private final ObjektService objekts;
    private final ObjektLiveSettings live;

    public Map<String, Object> alerts() {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> drops = releasedToday(dropAlerts(), LocalDate.now(ObjektLiveSettings.ZONE));
        body.put("catalogSig", objekts.catalogSignature());
        body.put("catalogCount", objekts.catalogCount());
        body.put("dropWindow", live.dropWindow() && !drops.isEmpty());
        body.put("objekts", drops);
        return body;
    }

    private List<Map<String, Object>> dropAlerts() {
        try {
            List<Map<String, Object>> drops = objekts.newestDrops();
            return drops == null ? List.of() : drops;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    /** 한국 날짜 기준으로 오늘 출시된 드롭만 최신 3개까지 남긴다. */
    static List<Map<String, Object>> releasedToday(List<Map<String, Object>> drops, LocalDate today) {
        if (drops == null || drops.isEmpty() || today == null) return List.of();
        List<Map<String, Object>> recent = new ArrayList<>();
        for (Map<String, Object> drop : drops) {
            LocalDate released = dropDate(drop == null ? null : drop.get("createdAt"));
            if (released == null || !released.equals(today)) continue;
            recent.add(drop);
            if (recent.size() == ObjektService.DROP_ALERT_LIMIT) break;
        }
        return recent;
    }

    static LocalDate dropDate(Object createdAt) {
        if (createdAt == null) return null;
        String text = createdAt.toString().trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return null;
        try {
            if (text.length() >= 19 && (text.charAt(10) == 'T' || text.charAt(10) == ' ')) {
                return instant(text).atZone(ObjektLiveSettings.ZONE).toLocalDate();
            }
            if (text.length() >= 10) return LocalDate.parse(text.substring(0, 10));
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        String local = text.length() > 19 && text.charAt(10) == ' ' ? text.replaceFirst(" ", "T") : text;
        if (local.length() > 19) local = local.substring(0, 19);
        return LocalDateTime.parse(local).atZone(ObjektLiveSettings.ZONE).toInstant();
    }
}

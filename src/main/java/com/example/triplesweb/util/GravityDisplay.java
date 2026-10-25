package com.example.triplesweb.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 한국 시간 표시용 모델. API 원본 날짜와 식별자는 변경하지 않는다. */
public final class GravityDisplay {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private record DateValue(LocalDate date, LocalTime time) {}

    private GravityDisplay() {}

    public static Map<String, Object> gravity(Map<String, Object> source) {
        Map<String, Object> view = new LinkedHashMap<>(source);
        String status = String.valueOf(source.getOrDefault("statusLabel", "상태 미정"));
        view.put("statusLabel", status);
        view.put("statusClass", switch (status) {
            case "진행 중" -> "ongoing";
            case "예정" -> "upcoming";
            case "종료" -> "ended";
            default -> "unknown";
        });
        view.put("periodLabel", range(source.get("entireStartDate"), source.get("entireEndDate")));
        return view;
    }

    public static Map<String, Object> poll(Map<String, Object> source) {
        Map<String, Object> view = new LinkedHashMap<>(source);
        view.put("periodLabel", range(source.get("startDate"), source.get("endDate")));
        DateValue reveal = parse(source.get("revealDate"));
        view.put("revealLabel", reveal == null ? "일정 미정" : full(reveal));
        return view;
    }

    public static String range(Object startValue, Object endValue) {
        DateValue start = parse(startValue), end = parse(endValue);
        if (start == null && end == null) return "일정 미정";
        if (start == null) return "시작 미정 ~ " + full(end);
        if (end == null) return full(start) + " ~ 종료 미정";
        if (start.date().equals(end.date())) {
            if (start.time() == null && end.time() == null) return start.date().toString();
            if (start.time() != null && end.time() != null) return full(start) + " ~ " + TIME.format(end.time());
        }
        return full(start) + " ~ " + full(end);
    }

    private static String full(DateValue value) {
        return value.date() + (value.time() == null ? "" : " " + TIME.format(value.time()));
    }

    private static DateValue parse(Object raw) {
        if (raw == null || raw.toString().isBlank()) return null;
        String value = raw.toString().trim();
        try {
            var date = OffsetDateTime.parse(value).atZoneSameInstant(KST);
            return new DateValue(date.toLocalDate(), date.toLocalTime());
        } catch (DateTimeParseException ignored) { }
        try {
            // 오프셋 없는 API 시각은 한국 현지 시각으로 취급한다.
            var date = LocalDateTime.parse(value);
            return new DateValue(date.toLocalDate(), date.toLocalTime());
        } catch (DateTimeParseException ignored) { }
        try {
            return new DateValue(LocalDate.parse(value), null);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}

package com.example.triplesweb.service;

import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
public class DateTimeFormatService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(KST);
    private static final DateTimeFormatter FMT_LDT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public String formatKst(Object dt) {
        if (dt == null) return "";
        if (dt instanceof LocalDateTime ldt) return ldt.format(FMT_LDT);
        if (dt instanceof LocalDate d)      return d.atStartOfDay().format(FMT_LDT);
        if (dt instanceof Instant i)        return FMT.format(i);
        if (dt instanceof ZonedDateTime z)  return z.withZoneSameInstant(KST).format(FMT);
        if (dt instanceof OffsetDateTime o) return o.atZoneSameInstant(KST).format(FMT);
        if (dt instanceof java.util.Date d) return FMT.format(d.toInstant());
        if (dt instanceof Long epochMs)     return FMT.format(Instant.ofEpochMilli(epochMs));
        return dt.toString();
    }
}

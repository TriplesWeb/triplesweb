package com.example.triplesweb.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 오브젝트 라이브 갱신 간격. 외부 API는 서버가 혼자 돌리고,
 * 브라우저 pulse는 이 값을 그대로 따른다. 드롭 창은 한국 시간 10:00–24:00.
 */
@Component
public class ObjektLiveSettings {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalTime DROP_START = LocalTime.of(10, 0);

    private final Clock clock;
    private final long pollMs;
    private final long dropPollMs;
    private final long refreshMs;
    private final long dropRefreshMs;
    private final long forceRefreshMs;
    private final long historyTtlMs;
    private final long dropHistoryTtlMs;

    @Autowired
    public ObjektLiveSettings(
            @Value("${objekt.live.poll-ms:20000}") long pollMs,
            @Value("${objekt.live.drop-poll-ms:10000}") long dropPollMs,
            @Value("${objekt.live.refresh-ms:${objekt.cache.refresh-ms:60000}}") long refreshMs,
            @Value("${objekt.live.drop-refresh-ms:30000}") long dropRefreshMs,
            @Value("${objekt.live.force-refresh-ms:600000}") long forceRefreshMs,
            @Value("${objekt.live.history-ttl-ms:120000}") long historyTtlMs,
            @Value("${objekt.live.drop-history-ttl-ms:60000}") long dropHistoryTtlMs) {
        this(Clock.system(ZONE), pollMs, dropPollMs, refreshMs, dropRefreshMs,
                forceRefreshMs, historyTtlMs, dropHistoryTtlMs);
    }

    ObjektLiveSettings(Clock clock, long pollMs, long dropPollMs, long refreshMs, long dropRefreshMs,
                       long forceRefreshMs, long historyTtlMs, long dropHistoryTtlMs) {
        this.clock = clock == null ? Clock.system(ZONE) : clock;
        this.pollMs = Math.max(5_000L, pollMs);
        this.dropPollMs = Math.max(5_000L, dropPollMs);
        this.refreshMs = Math.max(15_000L, refreshMs);
        this.dropRefreshMs = Math.max(15_000L, dropRefreshMs);
        this.forceRefreshMs = Math.max(60_000L, forceRefreshMs);
        this.historyTtlMs = Math.max(5_000L, historyTtlMs);
        this.dropHistoryTtlMs = Math.max(5_000L, dropHistoryTtlMs);
    }

    public static boolean dropWindow(Clock clock) {
        LocalTime time = LocalTime.now((clock == null ? Clock.system(ZONE) : clock).withZone(ZONE));
        return !time.isBefore(DROP_START);
    }

    public boolean dropWindow() {
        return dropWindow(clock);
    }

    public long pollMs() {
        return dropWindow() ? dropPollMs : pollMs;
    }

    public long refreshMs() {
        return dropWindow() ? dropRefreshMs : refreshMs;
    }

    public long forceRefreshMs() {
        return forceRefreshMs;
    }

    public long historyTtlMs() {
        return dropWindow() ? dropHistoryTtlMs : historyTtlMs;
    }

    public long tickMs() {
        return Math.min(pollMs(), refreshMs());
    }

    public boolean due(long lastMs) {
        if (lastMs <= 0) return true;
        return System.currentTimeMillis() - lastMs >= refreshMs();
    }
}

package com.example.triplesweb.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;


/** Two bounded scans: recent activity first, then a resumable historical scan. */
@Service
@Slf4j
@ConditionalOnProperty(name = "objekt.ingest.enabled", havingValue = "true", matchIfMissing = true)
public class ObjektActivityIngestor {
    private static final long OVERLAP_MS = 60_000;
    private static final long START_LOOKBACK_MS = 300_000;
    private final ObjektActivityClient client;
    private final ObjektIngestStore store;

    public ObjektActivityIngestor(ObjektActivityClient client, ObjektIngestStore store) {
        this.client = client; this.store = store;
    }

    @Scheduled(fixedDelayString = "${objekt.ingest.poll-ms:5000}", scheduler = "objektIngestScheduler")
    public void poll() {
        scan("live", 3);
        scan("backfill", 1);
    }

    void scan(String lane, int pageBudget) {
        try {
            for (int i = 0; i < pageBudget; i++) {
                ObjektIngestStore.State state = store.state(lane);
                long now = System.currentTimeMillis();
                if (lane.equals("backfill") && state.complete()
                        && state.lastSuccess() != null && now - state.lastSuccess() < 86_400_000) return;
                long head = state.cycleHead() == null ? now : state.cycleHead();
                long lower = state.lowerBound() == null
                        ? Math.max(0, (state.watermark() == 0 ? now - START_LOOKBACK_MS : state.watermark()) - OVERLAP_MS)
                        : state.lowerBound();
                ObjektActivityClient.Page page = client.fetch(state.cursor());
                boolean done = page.next() == null || (lane.equals("live") && page.events().stream().anyMatch(e -> e.time() < lower));
                if (!store.commit(state, page, now, head, lower, done)) return;
                if (done) return;
            }
        } catch (RuntimeException error) {
            // The last committed cursor survives; the next tick retries this page.
            log.warn("Objekt {} activity ingest retry: {}", lane, error.getMessage());
        }
    }
}

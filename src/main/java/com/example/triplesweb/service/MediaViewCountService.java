package com.example.triplesweb.service;

import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DB에 저장된 공식 미디어 조회수를 유튜브 statistics로 갱신한다.
 * 페이지 요청과 주기 작업이 같은 간격 안에서는 한 번만 호출하며, 한 사이클에 최대 50개만 돌린다.
 */
@Service
@RequiredArgsConstructor
public class MediaViewCountService {
    private final MediaRepository mediaRepository;
    private final YoutubeDataApiClient youtube;
    private final PlatformTransactionManager transactionManager;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastAttemptAt;
    private volatile int cursor;

    @Value("${media.views.refresh-ms:600000}")
    private long refreshMs;

    @Value("${media.views.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${media.views.refresh-ms:600000}", initialDelayString = "${media.views.initial-delay-ms:20000}")
    public void scheduledRefresh() {
        refreshIfDue();
    }

    public void refreshIfDue() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            if (refreshMs > 0 && now - lastAttemptAt < refreshMs) return;
            List<String> ids = nextBatch();
            if (ids.isEmpty()) {
                lastAttemptAt = System.currentTimeMillis();
                return;
            }
            Map<String, YoutubeDataApiClient.LiveVideo> live = youtube.fetchLive(ids);
            if (!live.isEmpty()) apply(live);
            lastAttemptAt = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    private List<String> nextBatch() {
        List<String> all = new TransactionTemplate(transactionManager).execute(status ->
                mediaRepository.findAllYoutubeIds());
        if (all == null || all.isEmpty()) return List.of();
        int size = all.size();
        int take = Math.min(Math.max(1, batchSize), size);
        int start = Math.floorMod(cursor, size);
        List<String> batch = new ArrayList<>(take);
        for (int i = 0; i < take; i++) batch.add(all.get((start + i) % size));
        cursor = start + take;
        return batch;
    }

    private void apply(Map<String, YoutubeDataApiClient.LiveVideo> live) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Media media : mediaRepository.findByYoutubeIdIn(live.keySet())) {
                YoutubeDataApiClient.LiveVideo next = live.get(media.getYoutubeId());
                if (next == null) continue;
                if (next.viewCount() != null && !next.viewCount().equals(media.getViewCount())) {
                    media.setViewCount(next.viewCount());
                }
                if (media.getPublishedOn() == null && next.publishedOn() != null) {
                    media.setPublishedOn(LocalDate.parse(next.publishedOn()));
                }
                if (next.duration() != null && !next.duration().isBlank() && missingDuration(media.getMediaDur())) {
                    media.setMediaDur(next.duration());
                }
            }
        });
    }

    private static boolean missingDuration(String duration) {
        if (duration == null || duration.isBlank()) return true;
        return "03:00".equals(duration) || "3:00".equals(duration);
    }
}

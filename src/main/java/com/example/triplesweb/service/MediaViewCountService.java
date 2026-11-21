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

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DB에 저장된 공식 미디어 조회수를 유튜브 statistics로 갱신한다.
 * 페이지 요청과 주기 작업이 같은 간격 안에서는 한 번만 호출한다.
 */
@Service
@RequiredArgsConstructor
public class MediaViewCountService {
    private final MediaRepository mediaRepository;
    private final YoutubeDataApiClient youtube;
    private final PlatformTransactionManager transactionManager;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastAttemptAt;

    @Value("${media.views.refresh-ms:60000}")
    private long refreshMs;

    @Scheduled(fixedDelayString = "${media.views.refresh-ms:60000}", initialDelayString = "${media.views.initial-delay-ms:20000}")
    public void scheduledRefresh() {
        refreshIfDue();
    }

    public void refreshIfDue() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            if (refreshMs > 0 && now - lastAttemptAt < refreshMs) return;
            List<String> ids = readIds();
            Map<String, Long> live = youtube.fetchViewCounts(ids);
            if (!live.isEmpty()) apply(live);
            lastAttemptAt = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    private List<String> readIds() {
        return new TransactionTemplate(transactionManager).execute(status ->
                mediaRepository.findAll().stream().map(Media::getYoutubeId).toList());
    }

    private void apply(Map<String, Long> live) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Media media : mediaRepository.findAll()) {
                Long next = live.get(media.getYoutubeId());
                if (next != null && !next.equals(media.getViewCount())) media.setViewCount(next);
            }
        });
    }
}

package com.example.triplesweb.config;

import com.example.triplesweb.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 배포 직후 기존 일반 영상의 탭을 제목과 공식 MV 목록에 맞춘다.
 * 쇼츠 여부는 YouTube 업로드 목록을 조회하는 채널 동기화에서 갱신한다.
 */
@Slf4j
@Component
@Order(3)
@Profile("!test")
@RequiredArgsConstructor
public class MediaTabReclassifyRunner implements ApplicationRunner {
    private final MediaService media;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int changed = media.reclassifyStoredTabs();
            if (changed > 0) log.info("Reclassified {} media tabs", changed);
        } catch (RuntimeException ex) {
            log.warn("Media tab reclassify skipped: {}", ex.getMessage());
        }
    }
}

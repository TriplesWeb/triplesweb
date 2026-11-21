package com.example.triplesweb.service;

import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.repository.MediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaViewCountServiceTest {
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private YoutubeDataApiClient youtube;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void replacesStoredCountAndSkipsAnotherCallInsideTheInterval() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Media media = Media.builder()
                .youtubeId("abcdefghijk")
                .tab("mv")
                .mediaTitle("제목")
                .mediaDur("3:11")
                .viewCount(10L)
                .build();
        when(mediaRepository.findAll()).thenReturn(List.of(media));
        when(youtube.fetchViewCounts(any())).thenReturn(Map.of("abcdefghijk", 3456L));

        MediaViewCountService service = new MediaViewCountService(mediaRepository, youtube, transactionManager);
        ReflectionTestUtils.setField(service, "refreshMs", 60_000L);

        service.refreshIfDue();
        service.refreshIfDue();

        assertThat(media.getViewCount()).isEqualTo(3456L);
        verify(youtube, times(1)).fetchViewCounts(any());
    }

    @Test
    void keepsStoredCountWhenYoutubeHasNoNumber() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Media media = Media.builder()
                .youtubeId("abcdefghijk")
                .tab("signal")
                .mediaTitle("제목")
                .mediaDur("1:00")
                .viewCount(10L)
                .build();
        when(mediaRepository.findAll()).thenReturn(List.of(media));
        when(youtube.fetchViewCounts(any())).thenReturn(Map.of());

        MediaViewCountService service = new MediaViewCountService(mediaRepository, youtube, transactionManager);
        ReflectionTestUtils.setField(service, "refreshMs", 60_000L);

        service.refreshIfDue();

        assertThat(media.getViewCount()).isEqualTo(10L);
    }
}

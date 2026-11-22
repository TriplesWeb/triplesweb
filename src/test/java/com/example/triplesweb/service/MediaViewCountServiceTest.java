package com.example.triplesweb.service;

import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.repository.MediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
        when(mediaRepository.findAllYoutubeIds()).thenReturn(List.of("abcdefghijk"));
        when(mediaRepository.findByYoutubeIdIn(any())).thenReturn(List.of(media));
        when(youtube.fetchLive(any())).thenReturn(Map.of(
                "abcdefghijk", new YoutubeDataApiClient.LiveVideo(3456L, "2024-05-06", "4:02")));

        MediaViewCountService service = new MediaViewCountService(mediaRepository, youtube, transactionManager);
        ReflectionTestUtils.setField(service, "refreshMs", 60_000L);
        ReflectionTestUtils.setField(service, "batchSize", 50);

        service.refreshIfDue();
        service.refreshIfDue();

        assertThat(media.getViewCount()).isEqualTo(3456L);
        assertThat(media.getPublishedOn()).isEqualTo(LocalDate.of(2024, 5, 6));
        assertThat(media.getMediaDur()).isEqualTo("3:11");
        verify(youtube, times(1)).fetchLive(any());
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
        when(mediaRepository.findAllYoutubeIds()).thenReturn(List.of("abcdefghijk"));
        when(youtube.fetchLive(any())).thenReturn(Map.of());

        MediaViewCountService service = new MediaViewCountService(mediaRepository, youtube, transactionManager);
        ReflectionTestUtils.setField(service, "refreshMs", 60_000L);
        ReflectionTestUtils.setField(service, "batchSize", 50);

        service.refreshIfDue();

        assertThat(media.getViewCount()).isEqualTo(10L);
    }

    @Test
    void refreshesAtMostFiftyIdsPerCycle() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) ids.add(String.format("%011d", i));
        when(mediaRepository.findAllYoutubeIds()).thenReturn(ids);
        when(youtube.fetchLive(any())).thenReturn(Map.of());

        MediaViewCountService service = new MediaViewCountService(mediaRepository, youtube, transactionManager);
        ReflectionTestUtils.setField(service, "refreshMs", 0L);
        ReflectionTestUtils.setField(service, "batchSize", 50);

        service.refreshIfDue();
        service.refreshIfDue();

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(youtube, times(2)).fetchLive(captor.capture());
        assertThat(captor.getAllValues().get(0)).hasSize(50).contains(ids.get(0)).doesNotContain(ids.get(50));
        assertThat(captor.getAllValues().get(1)).hasSize(50).contains(ids.get(50));
    }
}

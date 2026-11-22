package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaChannelSyncServiceTest {
    @Mock private YoutubeDataApiClient youtube;
    @Mock private MediaRepository mediaRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private MediaChannelSyncService service;

    @BeforeEach
    void setUp() {
        service = new MediaChannelSyncService(youtube, mediaRepository, artistRepository, transactionManager);
        ReflectionTestUtils.setField(service, "handle", "triplescosmos");
        ReflectionTestUtils.setField(service, "channelId", "UCJnL-TBcsYrF2SLs7tmiC8Q");
        ReflectionTestUtils.setField(service, "latestPages", 3);
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void insertsNewVideosAndSkipsLive() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist("triplescosmos", "UCJnL-TBcsYrF2SLs7tmiC8Q")).thenReturn("UU1");
        when(youtube.listUploadedVideoIds("UU1", 0)).thenReturn(List.of("abcdefghijk", "livevideo00"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                video("abcdefghijk", "tripleS 'Andless' Official MV", "PT3M11S", "none"),
                video("livevideo00", "Live now", "PT0S", "live")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        Artist seoyeon = artist("서연", "SeoYeon");
        when(artistRepository.findAll()).thenReturn(List.of(seoyeon));
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        Media saved = captor.getValue().get(0);
        assertThat(saved.getYoutubeId()).isEqualTo("abcdefghijk");
        assertThat(saved.getTab()).isEqualTo("mv");
        assertThat(saved.getMediaTitle()).isEqualTo("tripleS 'Andless' Official MV");
        assertThat(saved.getDescription()).isEmpty();
    }

    @Test
    void keepsAdminMetadataOnExistingRows() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(eq("UU1"), anyInt())).thenReturn(List.of("abcdefghijk"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                video("abcdefghijk", "새 제목 SIGNAL", "PT1M", "none")));
        Artist tagged = artist("서연", "SeoYeon");
        Media existing = Media.builder()
                .youtubeId("abcdefghijk")
                .tab("signal")
                .mediaTitle("관리자 제목")
                .mediaDur("3:11")
                .viewCount(99L)
                .description("\u200b관리자 설명")
                .publishedOn(LocalDate.of(2024, 1, 1))
                .build();
        existing.setArtists(Set.of(tagged));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(existing));
        when(artistRepository.findAll()).thenReturn(List.of(tagged));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(existing.getMediaTitle()).isEqualTo("관리자 제목");
        assertThat(existing.getTab()).isEqualTo("signal");
        assertThat(existing.getDescription()).isEqualTo("\u200b관리자 설명");
        assertThat(existing.getArtists()).containsExactly(tagged);
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    void matchesMemberNamesOnNewRows() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of("abcdefghijk"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                video("abcdefghijk", "서연 SIGNAL", "PT0M40S", "none")));
        Artist seoyeon = artist("서연", "SeoYeon");
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of(seoyeon));
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getArtists()).containsExactly(seoyeon);
        assertThat(captor.getValue().get(0).getTab()).isEqualTo("signal");
    }

    @Test
    void failsWhenKeyIsMissing() {
        when(youtube.hasApiKey()).thenReturn(false);
        assertThatThrownBy(service::syncFull)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API 키");
    }

    private static YoutubeDataApiClient.ChannelVideo video(String id, String title, String iso, String live) {
        String clock = "PT0S".equals(iso) ? null : "3:11";
        if ("PT0M40S".equals(iso)) clock = "0:40";
        return new YoutubeDataApiClient.ChannelVideo(id, title, "2024-05-06", clock, iso, 10L, live);
    }

    private static Artist artist(String name, String eng) {
        Artist artist = new Artist();
        artist.setName(name);
        artist.setEngName(eng);
        return artist;
    }
}

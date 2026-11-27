package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.NamuWikiYoutubeClient;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaChannelSyncServiceTest {
    @Mock private YoutubeDataApiClient youtube;
    @Mock private NamuWikiYoutubeClient namu;
    @Mock private MediaRepository mediaRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private PlatformTransactionManager transactionManager;
    private MediaChannelSyncService service;

    @Test
    void importsOnlyNewQualifiedHashtagVideosAndRemovesConfirmedFanUploads() {
        String publisher = "UCqq-ovGE01ErlXakPihhKDA";
        String smallPublisher = "UCEIi7zFR_wE23jFncVtd6-A";
        String fanPublisher = "UCHztIyDNlLlZ6JA85a6BdCw";
        String freshId = "Xrb5m74vT4o";
        String existingId = "HuD6ph0-ju0";
        String lowViewsId = "gOQ315I5yPs";
        String lowSubscribersId = "Yb0GtMc9Axc";
        String fanId = "PR6mJ59QMNc";
        Media existing = Media.builder().youtubeId(existingId).tab("shorts").build();
        Media fan = Media.builder().youtubeId(fanId).tab("external").curatedTab(true).build();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(youtube.searchHashtagVideoIds(eq("#트리플에스|#tripleS"), eq(2)))
                .thenReturn(List.of(freshId, existingId, lowViewsId, lowSubscribersId, fanId));
        when(youtube.fetchChannelAudiences(any())).thenReturn(java.util.Map.of(
                publisher, new YoutubeDataApiClient.ChannelAudience(10_000L, ""),
                smallPublisher, new YoutubeDataApiClient.ChannelAudience(9_999L, "")));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                taggedVideo(freshId, publisher, "방송사", 10_000L),
                taggedVideo(existingId, publisher, "방송사", 10_000L),
                taggedVideo(lowViewsId, publisher, "방송사", 9_999L),
                taggedVideo(lowSubscribersId, smallPublisher, "방송사", 10_000L),
                taggedVideo(fanId, fanPublisher, "팬편집 채널", 50_000L)));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(existing, fan));
        when(artistRepository.findAll()).thenReturn(List.of(artist("윤서연", "seoyeon")));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(existing.getTab()).isEqualTo("shorts");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> inserted = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(inserted.capture());
        assertThat(inserted.getValue()).extracting(Media::getYoutubeId, Media::getTab, Media::isCuratedTab)
                .containsExactly(tuple(freshId, "external", true));
        verify(mediaRepository).deleteAll(java.util.List.of(fan));
    }

    private static YoutubeDataApiClient.ChannelVideo taggedVideo(String id, String channel, String name, long views) {
        return new YoutubeDataApiClient.ChannelVideo(id, "외부 영상 #TripleS #서연", "2026-11-26",
                "3:15", "PT3M15S", views, "none", channel, name, "");
    }

    @BeforeEach
    void setUp() {
        service = new MediaChannelSyncService(youtube, namu, mediaRepository, artistRepository, transactionManager);
        ReflectionTestUtils.setField(service, "handle", "triplescosmos");
        ReflectionTestUtils.setField(service, "channelId", "UCJnL-TBcsYrF2SLs7tmiC8Q");
        ReflectionTestUtils.setField(service, "latestPages", 3);
        ReflectionTestUtils.setField(service, "externalLatestPages", 1);
        ReflectionTestUtils.setField(service, "externalFullPages", 2);
        ReflectionTestUtils.setField(service, "externalHandles", "1theKOriginals,SBSKPOP,SBSKPOP_ZOOM,KBSKpop,Mnet,MnetM2");
        ReflectionTestUtils.setField(service, "mvPlaylistId", "PLZlIsCBsz6M2cLA3F93I-snMa0sjAjAeY");
        ReflectionTestUtils.setField(service, "enabled", true);
        lenient().when(namu.videoIds()).thenReturn(List.of());
        lenient().when(namu.externalVideoIds()).thenReturn(List.of());
        lenient().when(youtube.searchVideoIds(any(), any(), anyInt())).thenReturn(List.of());
        lenient().when(youtube.resolveChannelId(any())).thenReturn(null);
        lenient().when(youtube.listUploadedVideoIds(eq("PLZlIsCBsz6M2cLA3F93I-snMa0sjAjAeY"), anyInt()))
                .thenReturn(List.of("abcdefghijk"));
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
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of("abcdefghijk"));
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
        assertThat(result.updated()).isEqualTo(1);
        assertThat(existing.getChannelId()).isEqualTo("UCJnL-TBcsYrF2SLs7tmiC8Q");
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
        when(youtube.listUploadedVideoIds("UU1", 0)).thenReturn(List.of("membervid01"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                video("membervid01", "서연 SIGNAL", "PT0M40S", "none")));
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
    void insertsOnlyNamuLinkedVideosAsExternalAndFancam() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("externvideo1", "fancamvideo"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "externvideo1", "GAP CRUSH #tripleS", "2024-05-06", "4:12", "PT4M12S",
                        10L, "none", "UC1thekxxxxxxxxxxxxxxx", "1theK Originals", "#tripleS"),
                new YoutubeDataApiClient.ChannelVideo(
                        "fancamvideo", "트리플에스 직캠 Rising", "2024-05-06", "3:11", "PT3M11S",
                        10L, "none", "UCSBSkpopxxxxxxxxxxxxx", "SBS KPOP ZOOM", "직캠 #tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Media::getTab).containsExactlyInAnyOrder("external", "fancam");
        verify(mediaRepository, never()).deleteAll(any());
    }

    @Test
    void missingNamuSourcesUseVerifiedPublisherUploadsAndKeepFiltering() {
        String officialChannel = "UCJnL-TBcsYrF2SLs7tmiC8Q";
        String publisherChannel = "UCMnetxxxxxxxxxxxxxxxxxx";
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist("triplescosmos", officialChannel)).thenReturn("UU1");
        when(youtube.resolveUploadsPlaylist(null, publisherChannel)).thenReturn("UU-Mnet");
        when(youtube.resolveChannelId("Mnet")).thenReturn(publisherChannel);
        when(youtube.listUploadedVideoIds("UU1", 3)).thenReturn(List.of());
        when(youtube.listUploadedVideoIds("UU-Mnet", 1))
                .thenReturn(List.of("musicbank01", "thirdparty1", "otherartist", "fanmadevid1", "unknownvid1"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo("musicbank01", "tripleS - Rising Music Bank", "2024-05-06",
                        "3:10", "PT3M10S", 10L, "none", publisherChannel, "Mnet K-POP", "#tripleS"),
                new YoutubeDataApiClient.ChannelVideo("thirdparty1", "tripleS 인터뷰", "2024-05-06",
                        "5:30", "PT5M30S", 10L, "none", publisherChannel, "Mnet K-POP", "#tripleS"),
                new YoutubeDataApiClient.ChannelVideo("otherartist", "LE SSERAFIM - EASY Music Bank #tripleS", "2024-05-06",
                        "3:50", "PT3M50S", 10L, "none", publisherChannel, "Mnet K-POP", "#tripleS"),
                new YoutubeDataApiClient.ChannelVideo("fanmadevid1", "tripleS Rising fan edit", "2024-05-06",
                        "3:14", "PT3M14S", 10L, "none", publisherChannel, "Mnet K-POP", "fanmade"),
                new YoutubeDataApiClient.ChannelVideo("unknownvid1", "tripleS 직캠 Rising", "2024-05-06",
                        "3:20", "PT3M20S", 10L, "none", "UCunknown", "Unrelated", "#tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());

        assertThat(service.syncSearch().inserted()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> saved = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Media::getYoutubeId, Media::getTab)
                .containsExactlyInAnyOrder(tuple("musicbank01", "music"), tuple("thirdparty1", "external"));
    }

    @Test
    void skipsHashtagVideosThatAreNotOnNamu() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of());
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "fanedit00001", "내가 좋아하는 #tripleS 모음", "2024-05-06", "3:00", "PT3M0S",
                        1L, "none", "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정", "팬메이드 #tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isZero();
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    void dropsFanMadeAndUnknownSourcesNotOnNamu() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("externvideo1"));
        Media junk = Media.builder()
                .youtubeId("fanedit00001")
                .tab("external")
                .mediaTitle("내가 좋아하는 #tripleS 모음")
                .mediaDur("3:00")
                .viewCount(1L)
                .description("")
                .channelId("UCfanxxxxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "externvideo1", "GAP CRUSH #tripleS", "2024-05-06", "4:12", "PT4M12S",
                        10L, "none", "UC1thekxxxxxxxxxxxxxxx", "1theK Originals", "#tripleS"),
                new YoutubeDataApiClient.ChannelVideo(
                        "fanedit00001", "내가 좋아하는 #tripleS 모음", "2024-05-06", "3:00", "PT3M0S",
                        1L, "none", "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정", "팬메이드 #tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(junk));
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> saved = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Media::getYoutubeId).containsExactly("externvideo1");
        verify(mediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(items -> {
            java.util.ArrayList<Media> dropped = new java.util.ArrayList<>();
            items.forEach(dropped::add);
            return dropped.size() == 1 && "fanedit00001".equals(dropped.get(0).getYoutubeId());
        }));
    }

    @Test
    void keepsExistingAllowedSourceWhenNotOnNamu() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("namuonly001"));
        Media kept = Media.builder()
                .youtubeId("keptsource01")
                .tab("external")
                .mediaTitle("GAP CRUSH #tripleS")
                .mediaDur("4:12")
                .viewCount(10L)
                .description("")
                .channelId("UC1thekxxxxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "namuonly001", "ASSEMBLE24 음방", "2024-05-17", "3:12", "PT3M12S",
                        10L, "none", "UCKBSkpopxxxxxxxxxxxxx", "KBS Kpop", "Music Bank"),
                new YoutubeDataApiClient.ChannelVideo(
                        "keptsource01", "GAP CRUSH #tripleS", "2024-05-06", "4:12", "PT4M12S",
                        10L, "none", "UC1thekxxxxxxxxxxxxxxx", "1theK Originals", "#tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(kept));
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isEqualTo(1);
        verify(mediaRepository, never()).deleteAll(any());
    }

    @Test
    void emptyNamuStillDropsUnknownUploaders() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of());
        Media previousNamu = Media.builder()
                .youtubeId("namuclip001")
                .tab("external")
                .mediaTitle("나무위키에 있던 클립")
                .mediaDur("3:11")
                .viewCount(3L)
                .description("")
                .channelId("UCotherxxxxxxxxxxxxx")
                .build();
        Media fan = Media.builder()
                .youtubeId("fanedit00001")
                .tab("external")
                .mediaTitle("팬메이드 모음")
                .mediaDur("3:00")
                .viewCount(1L)
                .description("")
                .channelId("UCfanxxxxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "namuclip001", "나무위키에 있던 클립", "2024-05-06", "3:11", "PT3M11S",
                        3L, "none", "UCotherxxxxxxxxxxxxx", "방송 클립 채널", "tripleS"),
                new YoutubeDataApiClient.ChannelVideo(
                        "fanedit00001", "팬메이드 모음", "2024-05-06", "3:00", "PT3M0S",
                        1L, "none", "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정", "팬메이드 #tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(previousNamu, fan));
        when(artistRepository.findAll()).thenReturn(List.of());

        service.syncFull();

        verify(mediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(items -> {
            java.util.ArrayList<Media> dropped = new java.util.ArrayList<>();
            items.forEach(dropped::add);
            return dropped.stream().map(Media::getYoutubeId).toList()
                    .containsAll(java.util.List.of("namuclip001", "fanedit00001"))
                    && dropped.size() == 2;
        }));
    }

    @Test
    void dropsUnofficialMcountdownFancamEvenWhenNamuIsEmpty() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of());
        Media unofficial = Media.builder()
                .youtubeId("i-JD4xdrld0")
                .tab("fancam")
                .mediaTitle("4K tripleS LYNN UNOFFICIAL FANCAM @MCOUNTDOWN")
                .mediaDur("3:05")
                .viewCount(12L)
                .description("")
                .channelId("UCfanxxxxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "i-JD4xdrld0",
                        "4K tripleS (트리플에스) LYNN (린) - 'Andless' UNOFFICIAL FANCAM @MCOUNTDOWN",
                        "2026-10-22", "3:05", "PT3M5S",
                        12L, "none", "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정", "UNOFFICIAL FANCAM")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(unofficial));
        when(artistRepository.findAll()).thenReturn(List.of());

        service.syncFull();

        verify(mediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(items -> {
            java.util.ArrayList<Media> dropped = new java.util.ArrayList<>();
            items.forEach(dropped::add);
            return dropped.size() == 1 && "i-JD4xdrld0".equals(dropped.get(0).getYoutubeId());
        }));
    }

    @Test
    void dropsOtherArtistMusicShowEvenFromAllowedChannel() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of());
        when(youtube.resolveChannelId("MnetM2")).thenReturn("UCmnetm2xxxxxxxxxxxxx");
        Media other = Media.builder()
                .youtubeId("_iKACnyXSWI")
                .tab("music")
                .mediaTitle("NCT WISH가 알려주는 이번 주 엠카운트다운 라인업은? #엠카운트다운 EP.950")
                .mediaDur("1:34")
                .viewCount(58307L)
                .description("")
                .channelId("UCmnetm2xxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "_iKACnyXSWI",
                        "NCT WISH가 알려주는 이번 주 엠카운트다운 라인업은? #엠카운트다운 EP.950",
                        "2026-10-20", "1:34", "PT1M34S",
                        58307L, "none", "UCmnetm2xxxxxxxxxxxxx", "M2", "#엠카운트다운")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(other));
        when(artistRepository.findAll()).thenReturn(List.of());

        service.syncFull();

        verify(mediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(items -> {
            java.util.ArrayList<Media> dropped = new java.util.ArrayList<>();
            items.forEach(dropped::add);
            return dropped.size() == 1 && "_iKACnyXSWI".equals(dropped.get(0).getYoutubeId());
        }));
    }

    @Test
    void skipsUnknownUploaderEvenWhenListedOnNamu() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("0RtEjnwfnPI"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "0RtEjnwfnPI",
                        "tripleS - Baby Flower 260614 @ INKIGAYO",
                        "2026-06-14", "3:24", "PT3M24S",
                        234L, "none", "UCfanxxxxxxxxxxxxxxx", "팬 업로드 계정", "Inkigayo")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isZero();
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    void dropsUnknownUploaderOnEveryTabEvenWhenNamuEmpty() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of());
        Media unofficial = Media.builder()
                .youtubeId("0RtEjnwfnPI")
                .tab("music")
                .mediaTitle("tripleS - Baby Flower 260614 @ INKIGAYO")
                .mediaDur("3:24")
                .viewCount(234L)
                .description("")
                .channelId("UCfanxxxxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "0RtEjnwfnPI",
                        "tripleS - Baby Flower 260614 @ INKIGAYO",
                        "2026-06-14", "3:24", "PT3M24S",
                        234L, "none", "UCfanxxxxxxxxxxxxxxx", "팬 업로드 계정", "Inkigayo")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(unofficial));
        when(artistRepository.findAll()).thenReturn(List.of());

        service.syncFull();

        verify(mediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(items -> {
            java.util.ArrayList<Media> dropped = new java.util.ArrayList<>();
            items.forEach(dropped::add);
            return dropped.size() == 1 && "0RtEjnwfnPI".equals(dropped.get(0).getYoutubeId());
        }));
    }

    @Test
    void curatedTabVideosAreNeitherRemovedNorMovedToShorts() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.externalVideoIds()).thenReturn(List.of("0hNuXQ0-FZM"));
        when(youtube.hasUploadFormat("UC1thekxxxxxxxxxxxxxxx", "0hNuXQ0-FZM", true)).thenReturn(true);
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo("0hNuXQ0-FZM", "배지전쟁", "2025-06-04", "1:00", "PT1M",
                        10L, "none", "UC1thekxxxxxxxxxxxxxxx", "1theK Originals", ""),
                new YoutubeDataApiClient.ChannelVideo("DRB_vCLbu7A", "아침 민낯 김채연", "2026-11-01", "7:02", "PT7M2S",
                        20L, "none", "UCchaeyeonxxxxxxxxxxx", "13년 차 신인 김채연", "")));
        Media shortClip = Media.builder().youtubeId("0hNuXQ0-FZM").tab("external").curatedTab(true).build();
        Media vlog = Media.builder().youtubeId("DRB_vCLbu7A").tab("external").curatedTab(true).build();
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(shortClip, vlog));
        when(artistRepository.findAll()).thenReturn(List.of());

        service.syncFull();

        assertThat(shortClip.getTab()).isEqualTo("external");
        verify(mediaRepository, never()).deleteAll(any());
    }

    @Test
    void insertsAllowedShortFormClipOnShortsTabEvenOverOneMinute() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("95eQDCrx8jQ"));
        when(youtube.hasUploadFormat("UCmnetm2xxxxxxxxxxxxx", "95eQDCrx8jQ", true)).thenReturn(true);
        when(youtube.resolveChannelId("MnetM2")).thenReturn("UCmnetm2xxxxxxxxxxxxx");
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "95eQDCrx8jQ",
                        "트리플에스가 말아주는 폭풍 칼군무 퍼포먼스 #MCOUNTDOWN #엠카운트다운 #tripleS",
                        "2026-10-24", "1:39", "PT1M39S",
                        2684L, "none", "UCmnetm2xxxxxxxxxxxxx", "M2", "#tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getTab()).isEqualTo("shorts");
        verify(mediaRepository, never()).deleteAll(any());
    }

    @Test
    void putsNamuSection3VideosOnExternalTab() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("externvideo1"));
        when(namu.externalVideoIds()).thenReturn(List.of("externvideo1"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "externvideo1", "GAP CRUSH #tripleS", "2024-05-06", "4:12", "PT4M12S",
                        10L, "none", "UC1thekxxxxxxxxxxxxxxx", "1theK Originals", "#tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Media::getTab).containsExactly("external");
    }

    @Test
    void dropsMusicShowTaggedWithOtherArtist() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of());
        when(youtube.resolveChannelId("MnetM2")).thenReturn("UCmnetm2xxxxxxxxxxxxx");
        Media other = Media.builder()
                .youtubeId("ald1clip001")
                .tab("music")
                .mediaTitle("라인업 #엠카운트다운 #알파드라이브원")
                .mediaDur("1:20")
                .viewCount(10L)
                .description("")
                .channelId("UCmnetm2xxxxxxxxxxxxx")
                .build();
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "ald1clip001", "라인업 #엠카운트다운 #알파드라이브원", "2026-02-01", "1:20", "PT1M20S",
                        10L, "none", "UCmnetm2xxxxxxxxxxxxx", "M2", "#알파드라이브원")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of(other));
        when(artistRepository.findAll()).thenReturn(List.of());

        service.syncFull();

        verify(mediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(items -> {
            java.util.ArrayList<Media> dropped = new java.util.ArrayList<>();
            items.forEach(dropped::add);
            return dropped.size() == 1 && "ald1clip001".equals(dropped.get(0).getYoutubeId());
        }));
    }

    @Test
    void insertsOtherArtistOfficialShortsWithMemberHashtag() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds(any(), anyInt())).thenReturn(List.of());
        when(youtube.searchVideoIds(any(), any(), anyInt())).thenReturn(List.of("iveshorts01"));
        when(youtube.hasUploadFormat("UCiveofficialxxxxxxxx", "iveshorts01", true)).thenReturn(true);
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "iveshorts01", "stage clip #tripleS #서연", "2026-03-01", "0:28", "PT0M28S",
                        9L, "none", "UCiveofficialxxxxxxxx", "IVE Official", "#tripleS")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of(artist("서연", "SeoYeon")));
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncFull();

        assertThat(result.inserted()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getTab()).isEqualTo("shorts");
    }

    @Test
    void officialMvOffPlaylistGoesToBehind() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds("UU1", 0)).thenReturn(List.of("notonthepl1"));
        when(youtube.listUploadedVideoIds(eq("PLZlIsCBsz6M2cLA3F93I-snMa0sjAjAeY"), anyInt()))
                .thenReturn(List.of("abcdefghijk"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                video("notonthepl1", "tripleS 'Andless' Official MV", "PT3M11S", "none")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getTab()).isEqualTo("behind");
    }

    @Test
    void latestSyncDoesNotReadNamuOrSearchYoutube() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds("UU1", 3)).thenReturn(List.of("abcdefghijk"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                video("abcdefghijk", "tripleS 'Andless' Official MV", "PT3M11S", "none")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncLatest();

        assertThat(result.inserted()).isEqualTo(1);
        verify(namu, never()).videoIds();
        verify(youtube, never()).searchVideoIds(any(), any(), anyInt());
        verify(youtube, never()).resolveChannelId(any());
    }

    @Test
    void searchSyncPutsNamuMusicBankOnMusicTab() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds("UU1", 3)).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("musicshow01"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "musicshow01", "Girls Never Die - tripleS - Music Bank", "2024-05-17", "3:12", "PT3M12S",
                        10L, "none", "UCKBSkpopxxxxxxxxxxxxx", "KBS Kpop", "Music Bank")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaChannelSyncService.Result result = service.syncSearch();

        assertThat(result.inserted()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Media::getTab).containsExactly("music");
    }

    @Test
    void firstScheduledLatestReadsNamuOnce() {
        stubSearchableNamuMusicBank();

        service.scheduledLatest();
        verify(namu, times(1)).videoIds();
        service.scheduledLatest();
        verify(namu, times(1)).videoIds();
        assertThat(service.externalSearchReady()).isTrue();
    }

    @Test
    void firstSearchFailureRetriesUntilSuccess() {
        when(youtube.hasApiKey()).thenReturn(false);
        service.scheduledLatest();
        verify(namu, never()).videoIds();
        assertThat(service.externalSearchReady()).isFalse();

        stubSearchableNamuMusicBank();
        service.scheduledLatest();
        verify(namu).videoIds();
        assertThat(service.externalSearchReady()).isTrue();
    }

    @Test
    void scheduledLatestSwallowsMissingKey() {
        when(youtube.hasApiKey()).thenReturn(false);
        service.scheduledLatest();
        verify(youtube, never()).listUploadedVideoIds(any(), anyInt());
    }

    @Test
    void failsWhenKeyIsMissing() {
        when(youtube.hasApiKey()).thenReturn(false);
        assertThatThrownBy(service::syncFull)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API 키");
    }

    private void stubSearchableNamuMusicBank() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(youtube.hasApiKey()).thenReturn(true);
        when(youtube.resolveUploadsPlaylist(any(), any())).thenReturn("UU1");
        when(youtube.listUploadedVideoIds("UU1", 3)).thenReturn(List.of());
        when(namu.videoIds()).thenReturn(List.of("musicshow01"));
        when(youtube.fetchChannelVideos(any())).thenReturn(List.of(
                new YoutubeDataApiClient.ChannelVideo(
                        "musicshow01", "Girls Never Die - tripleS - Music Bank", "2024-05-17", "3:12", "PT3M12S",
                        10L, "none", "UCKBSkpopxxxxxxxxxxxxx", "KBS Kpop", "Music Bank")));
        when(mediaRepository.findAllWithArtists()).thenReturn(List.of());
        when(artistRepository.findAll()).thenReturn(List.of());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

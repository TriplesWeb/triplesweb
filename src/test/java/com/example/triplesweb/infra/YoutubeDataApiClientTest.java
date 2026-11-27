package com.example.triplesweb.infra;

import com.example.triplesweb.service.MediaYoutubeCastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

class YoutubeDataApiClientTest {
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private YoutubeDataApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new YoutubeDataApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }

    @Test
    void readsViewCountsInBatchesOfFifty() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) ids.add(String.format("%011d", i));
        server.expect(requestTo(containsString("part=snippet,contentDetails,statistics")))
                .andExpect(requestTo(containsString(ids.get(0))))
                .andExpect(requestTo(containsString(ids.get(49))))
                .andRespond(withSuccess("""
                        {"items":[{"id":"00000000000","statistics":{"viewCount":"1200"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("part=snippet,contentDetails,statistics")))
                .andExpect(requestTo(containsString(ids.get(50))))
                .andRespond(withSuccess("""
                        {"items":[{"id":"00000000050","statistics":{"viewCount":"7"}}]}
                        """, MediaType.APPLICATION_JSON));

        Map<String, Long> counts = client.fetchViewCounts(ids);

        assertThat(counts).containsEntry("00000000000", 1200L).containsEntry("00000000050", 7L);
        server.verify();
    }

    @Test
    void resolvesUploadsPlaylistByHandleAndPagesPlaylistItems() {
        server.expect(requestTo(containsString("/channels")))
                .andExpect(requestTo(containsString("forHandle=triplescosmos")))
                .andRespond(withSuccess("""
                        {"items":[{"id":"UCJnL-TBcsYrF2SLs7tmiC8Q","contentDetails":{"relatedPlaylists":{"uploads":"UUJnL123"}}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/playlistItems")))
                .andExpect(requestTo(containsString("playlistId=UUJnL123")))
                .andRespond(withSuccess("""
                        {"nextPageToken":"CAUQAA","items":[{"contentDetails":{"videoId":"abcdefghijk"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/playlistItems")))
                .andExpect(requestTo(containsString("pageToken=CAUQAA")))
                .andRespond(withSuccess("""
                        {"items":[{"contentDetails":{"videoId":"lmnopqrstuv"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.resolveUploadsPlaylist("@triplescosmos", "")).isEqualTo("UUJnL123");
        assertThat(client.listUploadedVideoIds("UUJnL123", 0)).containsExactly("abcdefghijk", "lmnopqrstuv");
        server.verify();
    }

    @Test
    void resolvesAndCachesOnlyExactMemberHashtagsFromTheDescription() {
        server.expect(requestTo(containsString("id=abcdefghijk")))
                .andRespond(withSuccess("""
                        {"items":[{"id":"abcdefghijk","snippet":{
                          "title":"#김유연",
                          "description":"#tripleS #HaYeon #정하연 #서연 #지우팬"
                        }}]}
                        """, MediaType.APPLICATION_JSON));

        MediaYoutubeCastService cast = new MediaYoutubeCastService(client);
        assertThat(cast.findMembers("abcdefghijk")).containsExactly("정하연", "윤서연");
        assertThat(cast.findMembers("abcdefghijk")).containsExactly("정하연", "윤서연");
        server.verify();
    }

    @Test
    void marksLiveVideosFromChannelLookup() {
        server.expect(requestTo(containsString("part=snippet,contentDetails,statistics")))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"abcdefghijk","snippet":{"title":"MV","publishedAt":"2024-05-06T00:00:00Z","liveBroadcastContent":"none"},"contentDetails":{"duration":"PT3M11S"},"statistics":{"viewCount":"9"}},
                          {"id":"lmnopqrstuv","snippet":{"title":"Live","liveBroadcastContent":"live"},"contentDetails":{"duration":"PT0S"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<YoutubeDataApiClient.ChannelVideo> videos = client.fetchChannelVideos(List.of("abcdefghijk", "lmnopqrstuv"));
        assertThat(videos).hasSize(2);
        assertThat(videos.get(0).liveOrUpcoming()).isFalse();
        assertThat(videos.get(0).duration()).isEqualTo("3:11");
        assertThat(videos.get(1).liveOrUpcoming()).isTrue();
        server.verify();
    }

    @Test
    void searchesVideoIdsAndResolvesHandleToChannelId() {
        server.expect(requestTo(containsString("/search")))
                .andExpect(requestTo(containsString("q=%23tripleS")))
                .andExpect(requestTo(containsString("channelId=UCSBSkpopxxxxxxxxxxxxx")))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"abcdefghijk"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/channels")))
                .andExpect(requestTo(containsString("forHandle=SBSKPOP")))
                .andRespond(withSuccess("""
                        {"items":[{"id":"UCSBSkpopxxxxxxxxxxxxx"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.searchVideoIds("#tripleS", "UCSBSkpopxxxxxxxxxxxxx", 1)).containsExactly("abcdefghijk");
        assertThat(client.resolveChannelId("@SBSKPOP")).isEqualTo("UCSBSkpopxxxxxxxxxxxxx");
        server.verify();
    }

    @Test
    void pausesFurtherSearchesAfter429WhilePlaylistReadsContinue() {
        Instant before = Instant.now();
        server.expect(requestTo(containsString("/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600"));
        server.expect(requestTo(containsString("/playlistItems")))
                .andRespond(withSuccess("""
                        {"items":[{"contentDetails":{"videoId":"abcdefghijk"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.searchVideoIds("#tripleS", null, 1)).isEmpty();
        assertThat(client.searchVideoIds("#트리플에스", null, 1)).isEmpty();
        assertThat(client.listUploadedVideoIds("UUJnL123", 1)).containsExactly("abcdefghijk");
        assertThat((Instant) ReflectionTestUtils.getField(client, "searchRetryAt")).isAfterOrEqualTo(before.plusSeconds(3600));
        server.verify();
    }

    @Test
    void returnsNullWhenPlaylistPageFailsOrKeyIsMissing() {
        server.expect(requestTo(containsString("/playlistItems"))).andRespond(withServerError());
        assertThat(client.listUploadedVideoIds("UUJnL123", 0)).isNull();

        ReflectionTestUtils.setField(client, "apiKey", " ");
        assertThat(client.resolveUploadsPlaylist("triplescosmos", "UCJnL-TBcsYrF2SLs7tmiC8Q")).isNull();
        server.verify();
    }

    @Test
    void trimsWhitespaceAroundApiKeyBeforeRequestingVideos() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key \r\n");
        server.expect(requestTo(
                        "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,statistics&id=abcdefghijk&key=test-key"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"abcdefghijk","statistics":{"viewCount":"3"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchViewCounts(List.of("abcdefghijk"))).containsEntry("abcdefghijk", 3L);
        server.verify();
    }

    @Test
    void skipsRequestWhenKeyOrIdIsMissingAndIgnoresFailedCall() {
        ReflectionTestUtils.setField(client, "apiKey", " ");
        assertThat(client.fetchViewCounts(List.of("abcdefghijk"))).isEmpty();

        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        server.expect(requestTo(containsString("part=snippet,contentDetails,statistics"))).andRespond(withServerError());
        assertThat(client.fetchViewCounts(List.of("abcdefghijk", "not-an-id"))).isEmpty();
        server.verify();
    }
}

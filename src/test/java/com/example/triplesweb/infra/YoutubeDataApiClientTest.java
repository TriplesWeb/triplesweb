package com.example.triplesweb.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
    void returnsNullWhenPlaylistPageFailsOrKeyIsMissing() {
        server.expect(requestTo(containsString("/playlistItems"))).andRespond(withServerError());
        assertThat(client.listUploadedVideoIds("UUJnL123", 0)).isNull();

        ReflectionTestUtils.setField(client, "apiKey", " ");
        assertThat(client.resolveUploadsPlaylist("triplescosmos", "UCJnL-TBcsYrF2SLs7tmiC8Q")).isNull();
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

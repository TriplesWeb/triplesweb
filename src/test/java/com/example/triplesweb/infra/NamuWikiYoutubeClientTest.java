package com.example.triplesweb.infra;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class NamuWikiYoutubeClientTest {
    @Test
    void extractsWatchAndShortLinks() {
        String html = """
                <a href="https://www.youtube.com/watch?v=abcdefghijk">GAP CRUSH</a>
                <iframe src="https://www.youtube.com/embed/lmnopqrstuv"></iframe>
                https://youtu.be/shortsVideo1
                """;
        assertThat(NamuWikiYoutubeClient.extract(html))
                .contains("abcdefghijk", "lmnopqrstuv");
    }

    @Test
    void extractsYoutubeMacros() {
        assertThat(NamuWikiYoutubeClient.extract("[youtube(abcdefghijk)] 음방"))
                .containsExactly("abcdefghijk");
    }

    @Test
    void extractsTripleSSubpages() {
        String html = """
                <a href="/w/tripleS/%EC%9D%8C%EB%B0%A9">음방</a>
                [[tripleS/콘텐츠]]
                <a href="/w/ACCESS">ACCESS</a>
                """;
        assertThat(NamuWikiYoutubeClient.extractSubpages(html))
                .contains("tripleS/음방", "tripleS/콘텐츠");
        assertThat(NamuWikiYoutubeClient.extractAlbumTitles(html)).contains("ACCESS");
    }

    @Test
    void extractsOnlyExternalSectionYoutubeIds() {
        String raw = """
                == 자체 콘텐츠 ==
                [youtube(internvid01)]
                == 외부 콘텐츠 ==
                [youtube(externvid01)]
                https://youtu.be/gapcrush001
                == 리얼리티 ==
                [youtube(reality0001)]
                """;
        assertThat(NamuWikiYoutubeClient.extract(NamuWikiYoutubeClient.sliceExternalSection(raw)))
                .containsExactlyInAnyOrder("externvid01", "gapcrush001");
    }

    @Test
    void stopsRequestingBlockedRoutesAfterTheirFirst403() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(containsString("/raw/"))).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(containsString("/w/"))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        NamuWikiYoutubeClient client = new NamuWikiYoutubeClient(rest);
        assertThat(client.videoIds()).isEmpty();
        assertThat(client.externalVideoIds()).isEmpty();
        assertThat(client.videoIds()).isEmpty();
        server.verify();
    }
}

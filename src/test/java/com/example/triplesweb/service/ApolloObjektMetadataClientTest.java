package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApolloObjektMetadataClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void slugPrefersCatalogThenSeasonMemberNumber() {
        assertThat(ApolloObjektMetadataClient.slug(Map.of("slug", "cream02-kotone-356a")))
                .isEqualTo("cream02-kotone-356a");
        assertThat(ApolloObjektMetadataClient.slug(Map.of(
                "slug", "01a17092-abe1-72c0-925d-d7058df81a1f",
                "season", "Cream02", "member", "Kotone", "collectionNo", "356A")))
                .isEqualTo("cream02-kotone-356a");
        assertThat(ApolloObjektMetadataClient.slug(Map.of("slug", "not a slug"))).isEmpty();
    }

    @Test void parseKeepsCollectionEventAndEra() throws Exception {
        var root = mapper.readTree("""
                {"total":1,"data":{"collectionId":"cream02-kotone-356a",
                  "description":"Part of tripleS Official Character byteS Plush Keyring",
                  "event":{"name":"tripleS Official Character byteS","eventType":"merch",
                    "twitterUrl":"https://x.com/soundwave_korea/status/1",
                    "description":"Physical objekts included with the byteS Plush Keyring.",
                    "era":{"name":"<LOVE&POP> pt.1"}}}}
                """);
        var story = ApolloObjektMetadataClient.parse("cream02-kotone-356a", root);
        assertThat(story).isNotNull();
        assertThat(story.description()).contains("byteS Plush Keyring");
        assertThat(story.eventName()).isEqualTo("tripleS Official Character byteS");
        assertThat(story.eventDescription()).isEqualTo("Physical objekts included with the byteS Plush Keyring.");
        assertThat(story.eraName()).isEqualTo("<LOVE&POP> pt.1");
        assertThat(story.twitterUrl()).startsWith("https://x.com/");
        assertThat(story.toMap()).containsEntry("slug", "cream02-kotone-356a");
    }

    @Test void parseRejectsNonTwitterLinksAndEmptyPayloads() throws Exception {
        var empty = mapper.readTree("{\"data\":{}}");
        assertThat(ApolloObjektMetadataClient.parse("cream02-kotone-356a", empty)).isNull();
        var unsafe = mapper.readTree("{\"data\":{\"description\":\"ok\",\"event\":{\"twitterUrl\":\"javascript:alert(1)\"}}}");
        assertThat(ApolloObjektMetadataClient.parse("x", unsafe).twitterUrl()).isEmpty();
    }

    @Test void liveFetchCachesStoryAndSwallowsForbidden() {
        var builder = RestClient.builder().baseUrl("https://apollo.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        String body = "{\"data\":{\"description\":\"From Apollo\",\"event\":{\"name\":\"Drop\"}}}";
        server.expect(requestTo("https://apollo.test/api/objekts/metadata/cream02-kotone-356a"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var client = new ApolloObjektMetadataClient(builder.build(), true);
        var item = Map.<String, Object>of("slug", "cream02-kotone-356a");
        assertThat(client.story(item).orElseThrow().description()).isEqualTo("From Apollo");
        assertThat(client.story(item).orElseThrow().eventName()).isEqualTo("Drop");
        server.verify();

        var blocked = RestClient.builder().baseUrl("https://apollo.test");
        var blockedServer = MockRestServiceServer.bindTo(blocked).build();
        blockedServer.expect(requestTo("https://apollo.test/api/objekts/metadata/cream02-kotone-356a"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(new ApolloObjektMetadataClient(blocked.build(), true).story(item)).isEmpty();
        blockedServer.verify();
        assertThat(new ApolloObjektMetadataClient(RestClient.create(), false).story(item)).isEmpty();
    }
}

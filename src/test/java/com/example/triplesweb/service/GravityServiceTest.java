package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GravityServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CosmoTokenProvider tokens = mock(CosmoTokenProvider.class);

    @Test
    void unauthorizedGetRefreshesAndRetriesOnce() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        when(tokens.decodeResponse(anyString(), anyBoolean()))
                .thenAnswer(call -> mapper.readTree((String) call.getArgument(0)));
        when(tokens.isConfigured()).thenReturn(true);
        when(tokens.accessToken()).thenReturn("old");
        when(tokens.afterUnauthorized("old")).thenReturn("new");

        server.expect(requestTo("https://cosmo.test/bff/v3/gravities?artistId=tripleS"))
                .andExpect(header("Authorization", "Bearer old"))
                .andRespond(withUnauthorizedRequest());
        server.expect(requestTo("https://cosmo.test/bff/v3/gravities?artistId=tripleS"))
                .andExpect(header("Authorization", "Bearer new"))
                .andRespond(withSuccess(
                        "{\"ongoing\":[{\"id\":21,\"title\":\"Live\",\"polls\":[{\"id\":75}]}],\"upcoming\":[],\"past\":[]}",
                        MediaType.APPLICATION_JSON));

        var list = service(builder.build()).fetchGravityList("tripleS");
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("detailUrl", "/cosmo/gravity/21");
        verify(tokens).afterUnauthorized("old");
        server.verify();
    }

    @Test
    void gravityListDeduplicatesIdsAndCachesForTenMinutes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        when(tokens.decodeResponse(anyString(), anyBoolean()))
                .thenAnswer(call -> mapper.readTree((String) call.getArgument(0)));
        when(tokens.isConfigured()).thenReturn(true);
        when(tokens.accessToken()).thenReturn("token");

        String response = """
                {
                  "ongoing": [{"id": 21, "title": "Live", "polls": []}],
                  "upcoming": [
                    {"id": 21, "title": "Duplicate", "polls": []},
                    {"id": 22, "title": "Future", "startDate": "2026-10-01T10:00:00Z", "polls": []}
                  ],
                  "past": [{"id": 22, "title": "Duplicate past", "polls": []}]
                }
                """;
        server.expect(requestTo("https://cosmo.test/bff/v3/gravities?artistId=tripleS"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GravityService service = service(builder.build());
        var first = service.fetchGravityList("tripleS");
        var second = service.fetchGravityList("tripleS");

        assertThat(first).hasSize(2);
        assertThat(first.get(0)).containsEntry("title", "Live");
        assertThat(first.get(1)).containsEntry("title", "Future")
                .containsEntry("entireStartDate", "2026.10.01");
        assertThat(second).hasSize(2);
        server.verify();
    }

    private GravityService service(RestClient client) {
        return new GravityService(client, mapper, "https://cosmo.test", tokens);
    }
}

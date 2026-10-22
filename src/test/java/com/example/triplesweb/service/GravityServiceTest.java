package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GravityServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CosmoTokenProvider tokens = mock(CosmoTokenProvider.class);

    @Test void unauthorizedGetRefreshesAndRetriesOnce() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        when(tokens.decodeResponse(anyString(), anyBoolean())).thenAnswer(call -> mapper.readTree((String) call.getArgument(0)));
        when(tokens.isConfigured()).thenReturn(true);
        when(tokens.accessToken()).thenReturn("old");
        when(tokens.afterUnauthorized("old")).thenReturn("new");
        server.expect(requestTo("https://cosmo.test/bff/v3/gravities?artistId=tripleS"))
                .andExpect(header("Authorization", "Bearer old")).andRespond(withUnauthorizedRequest());
        server.expect(requestTo("https://cosmo.test/bff/v3/gravities?artistId=tripleS"))
                .andExpect(header("Authorization", "Bearer new"))
                .andRespond(withSuccess("{\"ongoing\":[{\"id\":21,\"title\":\"Live\",\"polls\":[{\"id\":75}]}],\"upcoming\":[],\"past\":[]}", MediaType.APPLICATION_JSON));
        var list = service(builder.build()).fetchGravityList("tripleS");
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("detailUrl", "/cosmo/gravity/21");
        verify(tokens).afterUnauthorized("old");
        server.verify();
    }

    private GravityService service(RestClient client) {
        return new GravityService(client, mapper, "https://cosmo.test", tokens);
    }
}

package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GravityServiceTest {
    private static final String LIST_URL = "https://cosmo.test/bff/v3/gravities?artistId=tripleS";
    private static final String LIST_JSON = "{\"ongoing\":[{\"id\":21,\"title\":\"Live\"}],\"upcoming\":[],\"past\":[]}";
    private GravityService gravity;
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

    @AfterEach void stopRefreshExecutor() {
        if (gravity != null) gravity.shutDownRefreshExecutor();
    }

    @Test void startupWarmupSharesOneColdLoadAndPageCallsStopWaitingBeforeItCompletes() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        configureToken();
        var upstreamEntered = new CountDownLatch(1);
        var releaseUpstream = new CountDownLatch(1);
        server.expect(requestTo(LIST_URL)).andRespond(request -> {
            upstreamEntered.countDown();
            await(releaseUpstream);
            return withSuccess(LIST_JSON, MediaType.APPLICATION_JSON).createResponse(request);
        });
        var service = service(builder.build());
        service.warmTripleSList();
        await(upstreamEntered);

        var executor = Executors.newFixedThreadPool(4);
        try {
            var requests = new ArrayList<Future<Optional<List<Map<String, Object>>>>>();
            for (int i = 0; i < 4; i++) {
                requests.add(executor.submit(() -> service.fetchGravityListForPage("tripleS")));
            }
            for (var response : requests) {
                assertThat(response.get(2, TimeUnit.SECONDS)).isEmpty();
            }
            releaseUpstream.countDown();
            assertThat(service.fetchGravityList("tripleS")).hasSize(1);
            assertThat(service.fetchGravityListForPage("tripleS")).hasValueSatisfying(items ->
                    assertThat(items.get(0)).containsEntry("title", "Live"));
            server.verify();
        } finally {
            releaseUpstream.countDown();
            executor.shutdownNow();
        }
    }

    @Test void retainsStaleListAndWaitsThirtySecondsToRetryAfterFailure() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        configureToken();
        var now = new AtomicLong(1);
        var failureRecorded = new CountDownLatch(1);
        var clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(call -> {
            long time = now.get();
            if (time == 60_001L && Thread.currentThread().getName().equals("gravity-cache-refresh")) {
                failureRecorded.countDown();
            }
            return time;
        });
        server.expect(requestTo(LIST_URL)).andRespond(withSuccess(LIST_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(LIST_URL)).andRespond(withServerError());
        server.expect(requestTo(LIST_URL)).andRespond(withSuccess(LIST_JSON.replace("Live", "Updated"), MediaType.APPLICATION_JSON));
        gravity = new GravityService(builder.build(), mapper, "https://cosmo.test", tokens, clock);
        var original = gravity.fetchGravityList("tripleS");
        now.set(60_001L);
        assertThat(gravity.fetchGravityListForPage("tripleS")).contains(original);
        await(failureRecorded);
        now.set(90_000L);
        assertThat(gravity.fetchGravityListForPage("tripleS")).contains(original);
        now.set(90_001L);
        assertThat(gravity.fetchGravityListForPage("tripleS")).contains(original);
        server.verify(java.time.Duration.ofSeconds(3));
    }

    private void configureToken() {
        when(tokens.isConfigured()).thenReturn(true);
        when(tokens.accessToken()).thenReturn("valid");
        when(tokens.decodeResponse(anyString(), anyBoolean())).thenAnswer(call -> mapper.readTree(call.<String>getArgument(0)));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private GravityService service(RestClient client) {
        gravity = new GravityService(client, mapper, "https://cosmo.test", tokens);
        return gravity;
    }
}

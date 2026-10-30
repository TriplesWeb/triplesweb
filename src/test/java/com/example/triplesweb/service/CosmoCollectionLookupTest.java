package com.example.triplesweb.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CosmoCollectionLookupTest {
    private HttpServer server;
    private CosmoCollectionService service;
    private final AtomicInteger profiles = new AtomicInteger();
    private final AtomicInteger holdings = new AtomicInteger();
    private int profileStatus = 200;
    private boolean missingSerial;
    private boolean indexFailure;
    private int holdingsPhase;
    private final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1700000000L);

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body;
            int status = 200;
            if (exchange.getRequestURI().getPath().contains("by-nickname")) {
                profiles.incrementAndGet(); status = profileStatus;
                body = "{\"nickname\":\"테스트\",\"address\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}";
            } else {
                holdings.incrementAndGet();
                if (indexFailure) { status = 503; body = "{}"; }
                else if (exchange.getRequestURI().getQuery() == null) {
                    body = "{\"objekts\":[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 24)
                            .mapToObj(this::objekt).toList()) + "],\"nextCursor\":{\"id\":\"24\",\"receivedAt\":\"2026-10-25T01:00:00Z\"}}";
                } else {
                    assertThat(exchange.getRequestURI().getQuery()).contains("cursor[receivedAt]", "cursor[id]=24");
                    body = "{\"objekts\":[" + objekt(24) + "," + objekt(25) + "," + objekt(26) + "]}";
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        var clock = org.mockito.Mockito.mock(java.time.Clock.class);
        org.mockito.Mockito.when(clock.instant()).thenAnswer(call -> java.time.Instant.ofEpochSecond(now.get()));
        service = new CosmoCollectionService(url, url, clock);
    }
    @AfterEach void close() { server.stop(0); }

    private String objekt(int n) {
        String slug = n == 26 && holdingsPhase == 1 ? "cream02-hayeon-368z" : "cream02-hayeon-367z";
        return "{\"id\":\"" + (30000000 + n) + "\",\"serial\":" + (missingSerial ? "null" : n)
                + ",\"slug\":\"" + slug + "\",\"collectionId\":\"Cream02 HaYeon 367Z #30000001\","
                + "\"member\":\"HaYeon\",\"season\":\"Cream02\",\"class\":\"Double\",\"frontImage\":\"https://example.com/card.webp\"}";
    }

    @Test @SuppressWarnings("unchecked") void loadsEveryCursorPageSerialsAndReusesCache() {
        var first = service.search("테스트", 0);
        assertThat(first).containsEntry("ownedCount", 26).containsEntry("totalPages", 2);
        var cards = (List<Map<String, Object>>) first.get("cards");
        assertThat(cards).hasSize(24);
        assertThat(cards.get(0)).containsEntry("serial", "1").containsEntry("collectionId", "Cream02 HaYeon 367Z")
                .containsEntry("member", "정하연")
                .doesNotContainKeys("tokenId", "address");
        var last = service.search("테스트", Integer.MAX_VALUE);
        assertThat(last).containsEntry("pageDisplay", 2).containsEntry("hasNext", false);
        assertThat((List<Map<String, Object>>) last.get("cards")).extracting(c -> c.get("serial")).containsExactly("25", "26");
        assertThat(profiles.get()).isEqualTo(1); assertThat(holdings.get()).isEqualTo(2);
    }

    @Test void missingNicknameIsExplicitAndDoesNotQueryHoldings() {
        profileStatus = 404;
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> service.search("없는사람", 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("없는 닉네임 입니다.");
        assertThat(profiles.get()).isEqualTo(1); assertThat(holdings.get()).isZero();
    }

    @Test void upstreamFailureIsNotReportedAsUnknownNickname() {
        profileStatus = 503;
        assertThatThrownBy(() -> service.search("테스트", 0)).isNotInstanceOf(IllegalArgumentException.class);
        assertThat(holdings.get()).isZero();
    }

    @Test @SuppressWarnings("unchecked") void missingSerialNeverFallsBackToTokenId() {
        missingSerial = true;
        var cards = (List<Map<String, Object>>) service.search("테스트", 0).get("cards");
        assertThat(cards.get(0)).containsEntry("hasSerial", false).containsEntry("serial", "").doesNotContainKey("tokenId");
    }

    @Test void failedHoldingsAreNotCachedAsEmptyOrPartialResults() {
        indexFailure = true;
        assertThatThrownBy(() -> service.search("테스트", 0)).isInstanceOf(RuntimeException.class);
        indexFailure = false;
        assertThat(service.search("테스트", 0)).containsEntry("ownedCount", 26);
    }
    @Test void incomingAndOutgoingHoldingsMoveBetweenMissingAndOwnedAfterShortCacheExpiry() {
        var catalog = org.mockito.Mockito.mock(ObjektService.class);
        var artists = org.mockito.Mockito.mock(com.example.triplesweb.repository.ArtistRepository.class);
        org.mockito.Mockito.when(artists.findAll()).thenReturn(List.of());
        org.mockito.Mockito.when(catalog.findCollections("triples")).thenReturn(List.of(
            Map.of("slug","cream02-hayeon-367z","member","HaYeon","season","Cream02","class","Double","collectionNo","367Z"),
            Map.of("slug","cream02-hayeon-368z","member","HaYeon","season","Cream02","class","Double","collectionNo","368Z")));
        var profiles = new CosmoProfileService(service, catalog, artists);
        var before = profiles.profile("테스트", "HaYeon", "members", "members", "missing");
        assertThat(before).containsEntry("checklistOwned", 1).containsEntry("checklistMissing", 1).containsEntry("checklistVisible", 1);
        holdingsPhase = 1;
        assertThat(profiles.profile("테스트", "HaYeon", "members", "members", "missing")).containsEntry("checklistMissing", 1);
        now.addAndGet(21);
        var incoming = profiles.profile("테스트", "HaYeon", "members", "members", "missing");
        assertThat(incoming).containsEntry("checklistOwned", 2).containsEntry("checklistMissing", 0).containsEntry("checklistVisible", 0);
        assertThat(incoming.get("profileVersion")).isNotEqualTo(before.get("profileVersion"));
        assertThat(profiles.profile("테스트", "HaYeon", "members", "members", "owned")).containsEntry("checklistVisible", 2);
        holdingsPhase = 2;
        now.addAndGet(21);
        assertThat(profiles.profile("테스트", "HaYeon", "members", "members", "missing"))
            .containsEntry("checklistOwned", 1).containsEntry("checklistMissing", 1).containsEntry("checklistVisible", 1);
    }

}

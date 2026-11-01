package com.example.triplesweb.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class CosmoCollectionLookupTest {
    private HttpServer server;
    private CosmoCollectionService service;
    private final AtomicInteger profiles = new AtomicInteger();
    private final AtomicInteger holdings = new AtomicInteger();
    private int profileStatus = 200;
    private boolean missingSerial;
    private boolean indexFailure;
    private volatile boolean laterPageFailure;
    private CountDownLatch continuePage = new CountDownLatch(0);

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
                    try { continuePage.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    assertThat(exchange.getRequestURI().getQuery()).contains("cursor[receivedAt]", "cursor[id]=24");
                    body = "{\"objekts\":[" + objekt(24) + "," + objekt(25) + "," + objekt(26) + "]}";
                    if (laterPageFailure) { status = 503; body = "{}"; }
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new CosmoCollectionService(url, url);
    }
    @AfterEach void close() { continuePage.countDown(); service.close(); server.stop(0); }

    private Map<String, Object> completed() {
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            var snapshot = service.search("테스트", 0);
            return Boolean.FALSE.equals(snapshot.get("loading")) && Integer.valueOf(26).equals(snapshot.get("ownedCount"));
        });
        return service.search("테스트", 0);
    }

    private String objekt(int n) {
        return "{\"id\":\"" + (30000000 + n) + "\",\"serial\":" + (missingSerial ? "null" : n)
                + ",\"slug\":\"cream02-hayeon-367z\",\"collectionId\":\"Cream02 HaYeon 367Z #30000001\","
                + "\"member\":\"HaYeon\",\"season\":\"Cream02\",\"class\":\"Double\",\"frontImage\":\"https://example.com/card.webp\"}";
    }

    @Test @SuppressWarnings("unchecked") void loadsEveryCursorPageSerialsAndReusesCache() {
        var first = completed();
        assertThat(first).containsEntry("ownedCount", 26).containsEntry("totalPages", 2);
        var cards = (List<Map<String, Object>>) first.get("cards");
        assertThat(cards).hasSize(24);
        assertThat(cards.get(0)).containsEntry("serial", "1").containsEntry("collectionId", "Cream02 HaYeon 367Z")
                .containsEntry("member", "정하연")
                .doesNotContainKeys("tokenId", "address");
        var last = service.search("테스트", 1);
        assertThat(last).containsEntry("pageDisplay", 2).containsEntry("hasNext", false);
        assertThat((List<Map<String, Object>>) last.get("cards")).extracting(c -> c.get("serial")).containsExactly("25", "26");
        assertThat(profiles.get()).isEqualTo(1); assertThat(holdings.get()).isEqualTo(2);
    }

    @Test void unfinishedPagesArePendingAndNeverAdvertisedAsAppendable() {
        continuePage = new CountDownLatch(1);
        assertThat(service.search("테스트", 1)).containsEntry("loading", true)
                .containsEntry("page", 0).containsEntry("nextPage", 0).containsEntry("hasMore", false);
        assertThat(service.search("테스트", 1, "", "", "", "26"))
                .containsEntry("hasCards", false).containsEntry("loading", true);
        continuePage.countDown();
        assertThat(completed()).containsEntry("ownedCount", 26);
        assertThat(service.search("테스트", 0, "", "", "", "26")).containsEntry("filteredCount", 1);
        assertThat(service.search("테스트", Integer.MAX_VALUE)).containsEntry("cards", List.of()).containsEntry("hasMore", false);
    }

    @Test void failedBackgroundPageIsRetriedInsteadOfCachingPartialSuccess() throws Exception {
        laterPageFailure = true;
        service.search("테스트", 0);
        var field = CosmoCollectionService.class.getDeclaredField("cache");
        field.setAccessible(true);
        var cached = ((Map<?, ?>) field.get(service)).values().iterator().next();
        var failure = cached.getClass().getDeclaredField("failure");
        failure.setAccessible(true);
        await().atMost(Duration.ofSeconds(5)).until(() -> failure.get(cached) != null);
        laterPageFailure = false;
        assertThat(completed()).containsEntry("ownedCount", 26);
        assertThat(profiles.get()).isEqualTo(2);
    }

    @Test void inactiveEntriesExpireAndNicknameCacheHasAHardLimit() throws Exception {
        profileStatus = 404;
        var field = CosmoCollectionService.class.getDeclaredField("cache");
        field.setAccessible(true);
        for (int i = 0; i < 101; i++) {
            String nickname = "missing" + i;
            assertThatThrownBy(() -> service.search(nickname, 0)).isInstanceOf(IllegalArgumentException.class);
        }
        var entries = (Map<?, ?>) field.get(service);
        assertThat(entries).hasSize(100);
        for (var cached : entries.values()) {
            var access = cached.getClass().getDeclaredField("lastAccessAt");
            access.setAccessible(true);
            access.setLong(cached, 0);
        }
        assertThatThrownBy(() -> service.search("new", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(entries).hasSize(1);
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
        assertThat(completed()).containsEntry("ownedCount", 26);
    }
}

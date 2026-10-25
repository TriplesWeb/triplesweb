package com.example.triplesweb.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ObjektMarketDataServiceTest {
    private HttpServer server;
    private ObjektMarketDataService service;
    private final AtomicReference<String> rarity = new AtomicReference<>("[{\"slug\":\"sample\",\"count\":524}]");
    private final AtomicReference<String> indexed = new AtomicReference<>("{\"tokenId\":\"30570441\",\"owner\":\"0xold\",\"transfers\":[{\"to\":\"0xaadab543abd013eeaf34eb50219b9316aa5bbcbe\",\"nickname\":\"FlexS5\"}]}");
    private final AtomicReference<String> metadata = new AtomicReference<>("{\"attributes\":[{\"trait_type\":\"Member\",\"value\":\"HaYeon\"},{\"trait_type\":\"Season\",\"value\":\"Cream02\"},{\"trait_type\":\"Collection\",\"value\":\"367Z\"}]}");
    private final AtomicReference<String> chain = new AtomicReference<>("{\"result\":\"0x000000000000000000000000aadab543abd013eeaf34eb50219b9316aa5bbcbe\"}");
    private final AtomicReference<String> profile = new AtomicReference<>("{\"address\":\"0xaadab543abd013eeaf34eb50219b9316aa5bbcbe\",\"nickname\":\"FlexS5\"}");
    private final AtomicReference<String> account = new AtomicReference<>("{\"address\":\"0xaadab543abd013eeaf34eb50219b9316aa5bbcbe\",\"nickname\":\"FlexS5\"}");
    private final java.util.concurrent.atomic.AtomicInteger profileCalls = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<String, Object> collection = Map.of("slug", "sample", "member", "HaYeon", "season", "Cream02", "collectionNo", "367Z");

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = path.endsWith("/rarity") ? rarity.get() : path.endsWith("/transfers") ? indexed.get()
                    : path.contains("nft-metadata") ? metadata.get()
                    : path.contains("/profiles/") ? profile.get() : path.contains("by-nickname") ? account.get() : chain.get();
            if (path.contains("/profiles/")) profileCalls.incrementAndGet();
            if (exchange.getRequestMethod().equals("POST")) {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(request).contains("eth_call", "6352211e" + "0".repeat(57) + new java.math.BigInteger("30570441").toString(16));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new ObjektMarketDataService(base, base, base, "0xcontract");
    }
    @AfterEach void close() { server.stop(0); }

    @Test void mintSnapshotSurvivesFailureAndDoesNotMutateCatalog() {
        service.refreshMintCounts();
        assertThat(service.withMintCounts(List.of(collection)).get(0)).containsEntry("mintCount", 524L);
        assertThat(collection).doesNotContainKey("mintCount");
        rarity.set("{\"error\":\"unavailable\"}");
        service.refreshMintCounts();
        assertThat(service.withMintCounts(List.of(collection)).get(0)).containsEntry("mintCount", 524L);
        assertThat(service.withMintCounts(List.of(Map.of("slug", "unknown"))).get(0)).doesNotContainKey("mintCount");
    }

    @Test void verifiesLiveOwnerInsteadOfTrustingIndexedOwner() {
        assertThat(service.owner(collection, 1)).containsEntry("status", "found")
                .containsEntry("address", "0xaadab543abd013eeaf34eb50219b9316aa5bbcbe")
                .containsEntry("tokenId", "30570441").containsEntry("nickname", "FlexS5");
    }

    @Test void rejectsWrongCollection() {
        metadata.set("{\"attributes\":[]}");
        assertThatThrownBy(() -> service.owner(collection, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test void unavailableChainNeverReportsIndexedOwner() {
        chain.set("{\"error\":{\"message\":\"unavailable\"}}");
        assertThatThrownBy(() -> service.owner(collection, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test void handlesHiddenMissingAndInvalidSerials() {
        indexed.set("{\"hide\":true,\"transfers\":[]}");
        assertThat(service.owner(collection, 1)).containsEntry("status", "hidden");
        indexed.set("{\"transfers\":[]}");
        assertThat(service.owner(collection, 1)).containsEntry("status", "not_found");
        assertThatThrownBy(() -> service.owner(collection, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(profileCalls.get()).isZero();
    }

    @Test void publicProfileResolvesNicknameMissingFromTransferHistoryAndCachesIt() {
        indexed.set("{\"tokenId\":\"30570441\",\"transfers\":[]}");
        assertThat(service.owner(collection, 1)).containsEntry("nickname", "FlexS5");
        assertThat(service.owner(collection, 2)).containsEntry("nickname", "FlexS5");
        assertThat(profileCalls.get()).isEqualTo(1);
    }

    @Test void staleOrWrongNicknameIsNeverAttachedToVerifiedOwner() {
        account.set("{\"address\":\"0xwrong\",\"nickname\":\"FlexS5\"}");
        assertThat(service.owner(collection, 1)).containsEntry("status", "found").containsEntry("nickname", "");
    }

    @Test void missingPublicProfileStillUsesVerifiedHistoryCandidate() {
        profile.set("{}");
        assertThat(service.owner(collection, 1)).containsEntry("nickname", "FlexS5");
    }
}

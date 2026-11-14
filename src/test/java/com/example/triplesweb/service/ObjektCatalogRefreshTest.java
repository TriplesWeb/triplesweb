package com.example.triplesweb.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ObjektCatalogRefreshTest {
    @Test
    void changedSeasonIsStoredAndSearchableWithoutChangingCollectionIdentity() throws Exception {
        AtomicReference<String> season = new AtomicReference<>("Cream01");
        HttpServer server = catalogServer(season);
        try {
            ObjektCatalogStore store = mock(ObjektCatalogStore.class);
            ObjektService service = new ObjektService("http://127.0.0.1:" + server.getAddress().getPort(),
                    3000, store, null);
            service.refreshCache();
            String originalSignature = service.catalogSignature();

            season.set("Cream02");
            service.refreshCache();
            var updated = service.findCollections("triples").get(0);
            assertThat(updated.get("season")).isEqualTo("Cream02");
            assertThat(SiteSearchService.objektMatches("Cream02", updated)).isTrue();
            assertThat(service.catalogSignature()).isNotEqualTo(originalSignature);
            verify(store, times(2)).replace(eq("triples"), anyList());

            service.refreshCache();
            verify(store, times(2)).replace(eq("triples"), anyList());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedStoreDoesNotMarkRefreshSuccessfulAndNextAttemptCanRecover() throws Exception {
        HttpServer server = catalogServer(new AtomicReference<>("Cream01"));
        try {
            ObjektCatalogStore store = mock(ObjektCatalogStore.class);
            doThrow(new IllegalStateException("database unavailable")).doNothing()
                    .when(store).replace(eq("triples"), anyList());
            ObjektService service = new ObjektService("http://127.0.0.1:" + server.getAddress().getPort(),
                    3000, store, null);

            service.refreshCache();
            assertThat(service.getLastSuccessfulRefreshMillis()).isZero();
            assertThat(service.findCollections("triples")).isEmpty();

            service.refreshCache();
            assertThat(service.getLastSuccessfulRefreshMillis()).isPositive();
            assertThat(service.findCollections("triples")).hasSize(1);
            verify(store, times(2)).replace(eq("triples"), anyList());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer catalogServer(AtomicReference<String> season) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections", exchange -> {
            byte[] body = ("{\"collections\":[{\"id\":\"1\",\"collectionId\":\"A\","
                    + "\"slug\":\"a\",\"member\":\"SeoYeon\",\"artist\":\"triples\","
                    + "\"collectionNo\":\"101\",\"season\":\"" + season.get() + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        return server;
    }
}

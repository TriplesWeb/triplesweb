package com.example.triplesweb.service;

import com.example.triplesweb.dto.ObjektCatalogDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ObjektCatalogRefreshTest {
    @Test
    void nonChainModeRefreshesSearchableMemoryWithoutWritingToCatalog() throws Exception {
        AtomicReference<String> season = new AtomicReference<>("Cream01");
        HttpServer server = catalogServer(season);
        try {
            ObjektCatalogStore store = mock(ObjektCatalogStore.class);
            ObjektService service = new ObjektService("http://127.0.0.1:" + server.getAddress().getPort(),
                    3000, store, null);
            service.loadStoredCatalog();
            service.refreshCache();
            String originalSignature = service.catalogSignature();

            season.set("Cream02");
            service.refreshCache();
            var updated = service.findCollections("triples").get(0);
            assertThat(updated.get("season")).isEqualTo("Cream02");
            assertThat(SiteSearchService.objektMatches("Cream02", updated)).isTrue();
            assertThat(service.catalogSignature()).isNotEqualTo(originalSignature);
            service.refreshCache();
            verify(store).load("triples");
            verifyNoMoreInteractions(store);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedExternalRefreshDoesNotMarkRefreshSuccessfulAndNextAttemptCanRecover() throws Exception {
        AtomicReference<String> season = new AtomicReference<>();
        HttpServer server = catalogServer(season);
        try {
            ObjektCatalogStore store = mock(ObjektCatalogStore.class);
            ObjektService service = new ObjektService("http://127.0.0.1:" + server.getAddress().getPort(),
                    3000, store, null);

            service.refreshCache();
            assertThat(service.getLastSuccessfulRefreshMillis()).isZero();
            assertThat(service.findCollections("triples")).isEmpty();

            season.set("Cream01");
            service.refreshCache();
            assertThat(service.getLastSuccessfulRefreshMillis()).isPositive();
            assertThat(service.findCollections("triples")).hasSize(1);
            verifyNoInteractions(store);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chainModeUsesStoredCatalogAndSkipsExternalRefresh() throws Exception {
        HttpServer server = catalogServer(new AtomicReference<>("Cream01"));
        try {
            ObjektCatalogStore store = mock(ObjektCatalogStore.class);
            ObjektService service = new ObjektService("http://127.0.0.1:" + server.getAddress().getPort(),
                    3000, store, null);
            ReflectionTestUtils.setField(service, "chainMints", true);
            service.loadStoredCatalog();
            service.refreshCache();

            assertThat(service.findCollections("triples")).isEmpty();
            verify(store).retainOnly("triples");
            verify(store).load("triples");
            verifyNoMoreInteractions(store);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void pendingCollectionIsSearchableAndIsReplacedByTheStoredCollection() {
        ObjektCatalogStore store = mock(ObjektCatalogStore.class);
        ObjektService service = new ObjektService("http://127.0.0.1", 3000, store, null);
        var row = ObjektCatalogDto.from(new ObjectMapper().createObjectNode()
                .put("id", "test-101z").put("slug", "test-101z").put("collectionId", "Cream02 SeoYeon 101Z")
                .put("artist", "triples").put("season", "Cream02").put("member", "SeoYeon")
                .put("collectionNo", "101Z")).toEntity();
        when(store.load("triples")).thenReturn(List.of()).thenReturn(List.of(row));

        service.reloadStoredCatalog();
        service.publishPendingCollections(List.of(row));
        assertThat(service.findByKey("test-101z")).containsEntry("mintPending", true);
        assertThat(service.findStoredByKey("test-101z")).isNull();
        String pendingSignature = service.catalogSignature();

        service.publishPendingCollections(List.of());
        assertThat(service.findByKey("test-101z")).containsEntry("mintPending", true);
        assertThat(service.catalogSignature()).isEqualTo(pendingSignature);

        service.reloadStoredCatalog();
        assertThat(service.findCollections("triples")).hasSize(1);
        assertThat(service.findByKey("test-101z")).doesNotContainKey("mintPending");
        assertThat(service.catalogSignature()).isNotEqualTo(pendingSignature);
        service.publishPendingCollections(List.of());
        assertThat(service.findByKey("test-101z")).isNotNull();
    }

    private static HttpServer catalogServer(AtomicReference<String> season) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections", exchange -> {
            if (season.get() == null) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
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

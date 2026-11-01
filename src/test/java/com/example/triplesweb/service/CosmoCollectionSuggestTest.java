package com.example.triplesweb.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CosmoCollectionSuggestTest {
    private HttpServer server;
    private CosmoCollectionService service;
    private final AtomicInteger searches = new AtomicInteger();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private String searchBody = "{\"results\":[{\"nickname\":\"hayeonfan\"},{\"nickname\":\"hayoon\"}]}";

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/search", exchange -> {
            searches.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = searchBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new CosmoCollectionService(url, url);
    }

    @AfterEach void close() {
        service.close();
        server.stop(0);
    }

    @Test void suggestReadsCosmoNicknamesFromThreeCharacters() {
        assertThat(service.suggestNicknames("hay", 8)).containsExactly("hayeonfan", "hayoon");
        assertThat(searches.get()).isEqualTo(1);
        assertThat(lastQuery.get()).contains("query=hay");
    }

    @Test void suggestIgnoresQueriesShorterThanThreeCharacters() {
        assertThat(service.suggestNicknames("ha", 8)).isEmpty();
        assertThat(service.suggestNicknames("  ot  ", 8)).isEmpty();
        assertThat(searches.get()).isZero();
    }

    @Test void suggestReusesCachedCosmoNicknames() {
        assertThat(service.suggestNicknames("hay", 8)).containsExactly("hayeonfan", "hayoon");
        searchBody = "{\"results\":[{\"nickname\":\"should-not-appear\"}]}";
        assertThat(service.suggestNicknames("hay", 8)).containsExactly("hayeonfan", "hayoon");
        assertThat(searches.get()).isEqualTo(1);
    }
}

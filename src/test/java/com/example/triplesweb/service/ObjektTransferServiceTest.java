package com.example.triplesweb.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ObjektTransferServiceTest {
    private HttpServer server;
    private ObjektTransferService service;
    private final String address = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final AtomicReference<String> body = new AtomicReference<>("{\"results\":[]}");
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String response;
            if (exchange.getRequestURI().getPath().contains("by-nickname"))
                response = "{\"address\":\"" + address + "\"}";
            else {
                query.set(exchange.getRequestURI().getRawQuery());
                response = body.get();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new ObjektTransferService(new ObjektIndexClient(base), base);
    }
    @AfterEach void close() { server.stop(0); }

    private String row(String from) {
        return "{\"transfer\":{\"from\":\"" + from + "\",\"to\":\"" + address
                + "\",\"timestamp\":\"2026-10-25T10:00:00Z\"},\"objekt\":{\"collectionId\":\"Cream02\",\"tokenId\":\"123\"},"
                + "\"nickname\":{\"from\":\"A\",\"to\":\"B\"}}";
    }
    @Test void readsOnePageWithoutLoadingHoldingsAndProjectsPublicFields() {
        body.set("{\"results\":[" + row("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb") + "]}");
        var result = service.account("B", "", "");
        assertThat(requests.get()).isEqualTo(2);
        assertThat(result).containsEntry("status", "available").containsEntry("hasNext", false);
        assertThat(result.toString()).contains("받음", "Cream02").doesNotContain(address, "tokenId");
    }
    @Test void forwardsCursorWithoutFetchingOtherPages() {
        service.account("B", "2026-10-25T10:00:00Z", "opaque+id");
        assertThat(java.net.URLDecoder.decode(query.get(), StandardCharsets.UTF_8))
                .contains("cursor[timestamp]=2026-10-25T10:00:00Z", "cursor[id]=opaque+id");
        assertThat(requests.get()).isEqualTo(2);
    }
    @Test void rejectsInvalidInputBeforeNetworkRequests() {
        assertThatThrownBy(() -> service.account("B", "bad", "id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.account("B", "", "id")).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }
    @Test void respectsHiddenResponseWithoutPublishingRows() {
        body.set("{\"hide\":true,\"results\":[" + row(address) + "]}");
        assertThat(service.account("B", "", "")).containsOnly(entry("status", "hidden"));
    }
    @Test void rejectsBrokenOrRepeatingCursorInsteadOfLosingHistory() {
        body.set("{\"results\":[],\"nextCursor\":{\"timestamp\":\"2026-10-25T10:00:00Z\",\"id\":\"id\"}}");
        assertThatThrownBy(() -> service.account("B", "", "")).isInstanceOf(IllegalStateException.class);
    }
}

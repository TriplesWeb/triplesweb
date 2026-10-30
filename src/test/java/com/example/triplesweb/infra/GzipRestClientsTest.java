package com.example.triplesweb.infra;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GzipRestClientsTest {

    @Test
    void decompressesGzipBodyAndClearsEncodingHeaders() throws Exception {
        byte[] plain = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(plain);
        }

        ClientHttpResponse raw = new FixedResponse(compressed.toByteArray(), "gzip");
        ClientHttpResponse decoded = GzipRestClients.decompressGzip()
                .intercept(null, null, (request, body) -> raw);

        assertThat(decoded.getHeaders().containsKey(HttpHeaders.CONTENT_ENCODING)).isFalse();
        assertThat(decoded.getHeaders().containsKey(HttpHeaders.CONTENT_LENGTH)).isFalse();
        assertThat(decoded.getBody().readAllBytes()).isEqualTo(plain);
        decoded.close();
    }

    @Test
    void leavesIdentityBodyUntouched() throws Exception {
        byte[] plain = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        ClientHttpResponse raw = new FixedResponse(plain, null);
        ClientHttpResponse decoded = GzipRestClients.decompressGzip()
                .intercept(null, null, (request, body) -> raw);

        assertThat(decoded).isSameAs(raw);
        assertThat(decoded.getBody().readAllBytes()).isEqualTo(plain);
    }

    private static final class FixedResponse implements ClientHttpResponse {
        private final byte[] body;
        private final HttpHeaders headers = new HttpHeaders();

        FixedResponse(byte[] body, String encoding) {
            this.body = body;
            headers.setContentLength(body.length);
            if (encoding != null) {
                headers.set(HttpHeaders.CONTENT_ENCODING, encoding);
            }
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public String getStatusText() {
            return "OK";
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
        }
    }
}

package com.example.triplesweb.infra;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * JDK {@link HttpClient}는 Accept-Encoding/gzip 해제를 하지 않는다.
 * objekt.top 카탈로그처럼 수 MB JSON은 압축 없이는 read timeout에 잘려
 * Jackson 추출 오류로 보인다. 요청에 gzip을 알리고 응답을 풀어 준다.
 */
public final class GzipRestClients {

    private GzipRestClients() {
    }

    public static RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .requestInterceptor(decompressGzip())
                .build();
    }

    static ClientHttpRequestInterceptor decompressGzip() {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            String encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                return new GzipDecodingClientHttpResponse(response);
            }
            return response;
        };
    }

    static final class GzipDecodingClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final HttpHeaders headers;
        private InputStream body;

        GzipDecodingClientHttpResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            this.headers = new HttpHeaders();
            this.headers.putAll(delegate.getHeaders());
            this.headers.remove(HttpHeaders.CONTENT_ENCODING);
            this.headers.remove(HttpHeaders.CONTENT_LENGTH);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = new GZIPInputStream(delegate.getBody());
            }
            return body;
        }

        @Override
        public void close() {
            try {
                if (body != null) {
                    body.close();
                }
            } catch (IOException ignored) {
                // close best-effort
            }
            delegate.close();
        }
    }
}

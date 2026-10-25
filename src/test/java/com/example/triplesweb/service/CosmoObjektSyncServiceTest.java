package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CosmoObjektSyncServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CosmoTokenProvider tokens = mock(CosmoTokenProvider.class);
    private final CosmoObjektSnapshotStore store = mock(CosmoObjektSnapshotStore.class);
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://cosmo.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private static String jwt(String subject, String suffix) {
        return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"sub\":\"" + subject + "\"}").getBytes()) + "." + suffix;
    }
    private CosmoObjektSyncService service() {
        when(tokens.accessToken()).thenReturn(jwt("account-a", "old"));
        when(tokens.decodeResponse(anyString(), anyBoolean())).thenAnswer(call -> mapper.readTree((String) call.getArgument(0)));
        return new CosmoObjektSyncService(builder.build(), tokens, store, mapper);
    }
    private static String page(int total, String id, String token, int copies) {
        return "{\"collectionCount\":" + total + ",\"collections\":[{\"collection\":{\"collectionId\":\"" + id
                + "\",\"artistName\":\"tripleS\",\"member\":\"JiWoo\",\"favoritedAt\":\"private\"},\"count\":" + copies
                + ",\"objekts\":[{\"metadata\":{\"collectionId\":\"" + id + "\",\"tokenId\":\"" + token
                + "\",\"objektNo\":7},\"inventory\":{\"status\":\"owned\",\"owner\":\"private-wallet\"}}]}]}";
    }
    private String url(int page) { return "https://cosmo.test/bff/v3/objekt-summaries?artistId=tripleS&order=newest&page=" + page + "&size=30"; }

    @Test void allPagesAreValidatedBeforeWritingAndPrivateFieldsAreExcluded() {
        var service = service();
        server.expect(requestTo(url(1))).andExpect(header("Authorization", "Bearer " + jwt("account-a", "old")))
                .andRespond(withSuccess(page(2, "one", "101", 1), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2))).andRespond(withSuccess(page(2, "two", "102", 1), MediaType.APPLICATION_JSON));
        var result = service.sync();
        assertThat(result.collections()).isEqualTo(2); assertThat(result.objekts()).isEqualTo(2);
        verify(store).save(argThat(scope -> scope.matches("[0-9a-f]{64}")), eq(2), argThat(cards -> cards.size() == 2
                && cards.get(0).serial() == 7 && !cards.get(0).collectionJson().contains("private")));
        server.verify();
    }
    @Test void missingCopiesNeverReplaceStoredData() {
        var service = service();
        server.expect(requestTo(url(1))).andRespond(withSuccess(page(1, "one", "101", 2), MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::sync).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store);
    }
    @Test void failedLaterPageNeverWritesAndDoesNotExposeUpstreamBody() {
        var service = service();
        server.expect(requestTo(url(1))).andRespond(withSuccess(page(2, "one", "101", 1), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2))).andRespond(withServerError().body("credential-secret"));
        assertThatThrownBy(service::sync).hasMessageNotContaining("credential-secret");
        verifyNoInteractions(store);
    }
    @Test void unauthorizedRefreshesOnceAndUsesBearer() {
        var service = service();
        when(tokens.afterUnauthorized(anyString())).thenReturn(jwt("account-a", "new"));
        server.expect(requestTo(url(1))).andRespond(withUnauthorizedRequest());
        server.expect(requestTo(url(1))).andExpect(header("Authorization", "Bearer " + jwt("account-a", "new")))
                .andRespond(withSuccess(page(1, "one", "101", 1), MediaType.APPLICATION_JSON));
        assertThat(service.sync().objekts()).isEqualTo(1);
        verify(tokens, times(1)).afterUnauthorized(anyString()); server.verify();
    }
    @Test void refreshedAccountCannotOverwriteAnotherAccountSnapshot() {
        var service = service();
        when(tokens.afterUnauthorized(anyString())).thenReturn(jwt("account-b", "new"));
        server.expect(requestTo(url(1))).andRespond(withUnauthorizedRequest());
        assertThatThrownBy(service::sync).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store);
    }
}

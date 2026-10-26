package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Page-scoped public account history. Resolves identity without loading the account's holdings. */
@Service
public class ObjektTransferService {
    private final ObjektIndexClient index;
    private final RestClient cosmo;

    public ObjektTransferService(ObjektIndexClient index,
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String cosmoUrl) {
        this.index = index;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(6));
        cosmo = RestClient.builder().baseUrl(cosmoUrl).requestFactory(factory).build();
    }

    public Map<String, Object> account(String nickname, String timestamp, String id) {
        if (nickname == null || nickname.isBlank() || nickname.length() > 40
                || nickname.chars().anyMatch(Character::isWhitespace) || nickname.contains("/"))
            throw new IllegalArgumentException("닉네임을 올바르게 입력해 주세요.");
        if (timestamp.isBlank() != id.isBlank() || id.length() > 200)
            throw new IllegalArgumentException("다음 페이지 정보가 올바르지 않습니다.");
        if (!timestamp.isBlank()) {
            try { Instant.parse(timestamp); }
            catch (RuntimeException e) { throw new IllegalArgumentException("다음 페이지 정보가 올바르지 않습니다."); }
        }
        JsonNode profile;
        try { profile = cosmo.get().uri("/bff/v3/users/by-nickname/{nickname}", nickname).retrieve().body(JsonNode.class); }
        catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Map.of("status", "not_found");
            throw e;
        }
        String address = profile == null ? "" : profile.path("address").asText("");
        if (!address.matches("0x[0-9a-fA-F]{40}")) throw new IllegalStateException("계정을 확인하지 못했습니다.");
        JsonNode response = index.accountHistory(address, timestamp, id);
        if (response.path("hide").asBoolean()) return Map.of("status", "hidden");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            JsonNode transfer = item.path("transfer"), card = item.path("objekt");
            String from = transfer.path("from").asText(""), to = transfer.path("to").asText("");
            if (!from.matches("0x[0-9a-fA-F]{40}") || !to.matches("0x[0-9a-fA-F]{40}")
                    || (!address.equalsIgnoreCase(from) && !address.equalsIgnoreCase(to)))
                throw new IllegalStateException("계정과 전송 내역이 일치하지 않습니다.");
            String direction = address.equalsIgnoreCase(from) ? (address.equalsIgnoreCase(to) ? "자기 전송" : "보냄") : "받음";
            rows.add(Map.of("timestamp", parseTime(transfer.path("timestamp").asText("")),
                    "direction", direction, "collectionId", safeText(card.path("collectionId")),
                    "fromNickname", safeText(item.path("nickname").path("from")),
                    "toNickname", safeText(item.path("nickname").path("to"))));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "available"); result.put("source", "objekt.top"); result.put("transfers", List.copyOf(rows));
        JsonNode cursor = response.path("nextCursor");
        boolean more = !cursor.isMissingNode() && !cursor.isNull();
        if (more) {
            String nextTime = parseTime(cursor.path("timestamp").asText("")), nextId = cursor.path("id").asText("");
            if (nextId.isBlank() || nextId.length() > 200 || rows.isEmpty()
                    || (nextTime.equals(timestamp) && nextId.equals(id)))
                throw new IllegalStateException("다음 페이지를 확인하지 못했습니다.");
            result.put("cursorTimestamp", nextTime); result.put("cursorId", nextId);
        }
        result.put("hasNext", more);
        return result;
    }

    private static String safeText(JsonNode node) {
        String value = node.asText("");
        if (value.length() > 300) throw new IllegalStateException("전송 응답이 올바르지 않습니다.");
        return value;
    }
    private static String parseTime(String value) {
        try { return Instant.parse(value).toString(); }
        catch (RuntimeException e) { throw new IllegalStateException("전송 일시가 올바르지 않습니다."); }
    }
}

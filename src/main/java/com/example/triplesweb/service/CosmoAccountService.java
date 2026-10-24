package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 연결된 COSMO 계정의 닉네임과, 그 지갑이 지금 들고 있는 오브젝트를 읽는다.
 * 보유 카드는 지갑 주소로 Abstract 전송을 바로 조회한다. 전체 체인을 MariaDB에 쌓지 않는다.
 */
@Service
public class CosmoAccountService {
    private static final List<String> PROFILE_PATHS = List.of(
            "/user/v1/me",
            "/bff/v1/users/me",
            "/bff/v3/users/me"
    );

    private final GravityService gravity;
    private final CosmoCollectionService collection;

    public CosmoAccountService(GravityService gravity, CosmoCollectionService collection) {
        this.gravity = gravity;
        this.collection = collection;
    }

    public Map<String, Object> load() {
        JsonNode me = firstProfile();
        if (me == null) {
            throw new IllegalStateException("연결된 COSMO 계정 정보를 가져오지 못했습니다. 그래비티의 COSMO 계정 연결을 다시 확인해 주세요.");
        }
        String nickname = firstText(me, "nickname", "profile.nickname");
        String address = firstText(me, "address", "profile.address");
        String image = GravityService.safeImage(firstText(me, "profileImageUrl", "profile.profileImageUrl"));

        List<Map<String, Object>> objekts = List.of();
        long ownedCount = 0;
        String holdingNote = "";
        String displayNickname = nickname.isBlank() ? "COSMO" : nickname;
        if (!nickname.isBlank()) {
            try {
                Map<String, Object> found = collection.search(nickname, 0);
                objekts = cards(found.get("cards"));
                ownedCount = number(found.get("ownedCount"));
                if (address.isBlank()) address = text(found.get("address"));
                if (image.isBlank()) image = GravityService.safeImage(text(found.get("profileImageUrl")));
                String resolved = text(found.get("nickname"));
                if (!resolved.isBlank()) displayNickname = resolved;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                holdingNote = ex.getMessage() == null ? "" : ex.getMessage();
            }
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("nickname", displayNickname);
        view.put("hasAddress", !address.isBlank());
        view.put("addressShort", shorten(address));
        view.put("profileImageUrl", image);
        view.put("hasProfileImage", !image.isBlank());
        view.put("ownedCount", ownedCount);
        view.put("shownCount", objekts.size());
        view.put("ownedTruncated", ownedCount > objekts.size());
        view.put("holdingNote", holdingNote);
        view.put("hasHoldingNote", !holdingNote.isBlank());
        view.put("ownedObjekts", objekts);
        view.put("hasOwned", !objekts.isEmpty());
        view.put("hasCollectionLink", !nickname.isBlank());
        view.put("collectionHref", "/cosmo/collection?nickname=" + UriUtils.encodeQueryParam(displayNickname, StandardCharsets.UTF_8));
        return view;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cards(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) return List.of();
        if (!(list.get(0) instanceof Map<?, ?>)) return List.of();
        return (List<Map<String, Object>>) list;
    }

    private JsonNode firstProfile() {
        if (!gravity.hasCosmoToken()) {
            throw new IllegalStateException("COSMO 인증이 아직 연결되지 않았습니다.");
        }
        for (String path : PROFILE_PATHS) {
            var response = gravity.tryGet(path);
            if (response.isEmpty()) continue;
            JsonNode node = response.get();
            if (node.has("profile") && node.get("profile").isObject()
                    && (node.get("profile").has("nickname") || node.get("profile").has("address"))) {
                node = node.get("profile");
            }
            if (!firstText(node, "nickname", "address").isBlank()) return node;
        }
        return null;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstText(JsonNode node, String... paths) {
        if (node == null) return "";
        for (String path : paths) {
            JsonNode current = node;
            for (String part : path.split("\\.")) {
                if (current == null) break;
                current = current.get(part);
            }
            if (current != null && !current.isNull() && !current.isContainerNode()) {
                String value = current.asText("");
                if (!value.isBlank()) return value;
            }
        }
        return "";
    }

    private static String shorten(String address) {
        if (address == null || address.length() < 12) return address == null ? "" : address;
        return address.substring(0, 6) + "…" + address.substring(address.length() - 4);
    }
}

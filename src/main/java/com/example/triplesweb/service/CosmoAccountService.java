package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 연결된 COSMO: The Gate 계정의 닉네임, 소유 오브젝트, 스캔 시리얼을 읽는다.
 */
@Service
public class CosmoAccountService {
    private static final int OWNED_LIMIT = 48;
    private static final List<String> PROFILE_PATHS = List.of(
            "/user/v1/me",
            "/bff/v1/users/me",
            "/bff/v3/users/me"
    );
    private static final List<String> SCAN_PATHS = List.of(
            "/bff/v3/activity/histories?artistName=tripleS&historyType=objekt_receive",
            "/bff/v1/scan-histories",
            "/objekt/v1/scan-histories",
            "/bff/v1/users/me/scans"
    );

    private final GravityService gravity;
    private final AbstractObjektReadService chainOwned;
    private final RestClient publicCosmo;

    public CosmoAccountService(GravityService gravity, AbstractObjektReadService chainOwned) {
        this.gravity = gravity;
        this.chainOwned = chainOwned;
        this.publicCosmo = RestClient.builder().baseUrl("https://api.cosmo.fans").build();
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
        for (String path : ownedPaths(address)) {
            var response = gravity.tryGet(path);
            if (response.isEmpty()) continue;
            List<Map<String, Object>> parsed = extractObjekts(response.get());
            if (!parsed.isEmpty()) {
                objekts = parsed;
                break;
            }
        }
        if (address.isBlank() && !nickname.isBlank() && !"COSMO".equals(nickname)) {
            address = publicAddress(nickname);
        }
        List<Map<String, Object>> chain = chainOwned.owned(address, OWNED_LIMIT);
        long chainCount = chainOwned.ownedCount(address);
        boolean ownedTruncated = objekts.size() > OWNED_LIMIT;
        if (chainCount > 0) {
            objekts = chain;
            ownedTruncated = chainCount > OWNED_LIMIT;
        } else if (ownedTruncated) {
            objekts = objekts.subList(0, OWNED_LIMIT);
        }

        List<Map<String, Object>> scans = List.of();
        for (String path : SCAN_PATHS) {
            var response = gravity.tryGet(path);
            if (response.isEmpty()) continue;
            List<Map<String, Object>> parsed = extractScans(response.get());
            if (!parsed.isEmpty()) {
                scans = parsed;
                break;
            }
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("nickname", nickname.isBlank() ? "COSMO" : nickname);
        view.put("hasAddress", !address.isBlank());
        view.put("addressShort", shorten(address));
        view.put("profileImageUrl", image);
        view.put("hasProfileImage", !image.isBlank());
        view.put("ownedCount", chainCount > 0 ? chainCount : objekts.size());
        view.put("ownedTruncated", ownedTruncated);
        view.put("chainSyncNote", chainOwned.progressNote());
        view.put("ownedObjekts", objekts);
        view.put("hasOwned", !objekts.isEmpty());
        view.put("scans", scans);
        view.put("hasScans", !scans.isEmpty());
        view.put("scanCount", scans.size());
        return view;
    }

    private String publicAddress(String nickname) {
        try {
            JsonNode node = publicCosmo.get()
                    .uri("/bff/v3/users/by-nickname/" + UriUtils.encodePath(nickname, StandardCharsets.UTF_8))
                    .retrieve().body(JsonNode.class);
            return node == null ? "" : node.path("address").asText("");
        } catch (RuntimeException ex) {
            return "";
        }
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

    private static List<String> ownedPaths(String address) {
        List<String> paths = new ArrayList<>();
        if (address != null && !address.isBlank()) {
            String encoded = UriUtils.encodePath(address, StandardCharsets.UTF_8);
            paths.add("/objekt/v1/owned-by/" + encoded + "?sort=newest");
            paths.add("/bff/v3/objekts/owned-by/" + encoded + "?sort=newest");
        }
        paths.add("/objekt/v1/owned/collection-groups?artistName=tripleS");
        paths.add("/bff/v3/objekts?artistName=tripleS&sort=newest");
        paths.add("/objekt/v1/owned-by/me?sort=newest");
        return paths;
    }

    static List<Map<String, Object>> extractCollectionGroups(JsonNode root) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (root == null) return rows;
        JsonNode groups = root.get("collections");
        if (groups == null || !groups.isArray()) return rows;
        for (JsonNode group : groups) {
            JsonNode collection = group.path("collection");
            if (!collection.isObject()) collection = group;
            JsonNode owned = group.path("objekts");
            if (owned.isArray() && !owned.isEmpty()) {
                for (JsonNode item : owned) rows.add(ownedRow(collection, item));
            } else if (!firstText(collection, "collectionId").isBlank()) {
                rows.add(ownedRow(collection, collection));
            }
        }
        return rows;
    }

    private static Map<String, Object> ownedRow(JsonNode collection, JsonNode item) {
        String thumbnail = GravityService.safeImage(firstText(collection, "thumbnailImage", "frontImage"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("collectionId", firstText(collection, "collectionId"));
        row.put("member", firstText(collection, "member", "artistMember.name"));
        row.put("season", firstText(collection, "season"));
        row.put("objektClass", firstText(collection, "class"));
        row.put("tokenId", firstText(item, "metadata.tokenId", "tokenId"));
        row.put("objektNo", firstText(item, "metadata.objektNo", "objektNo"));
        row.put("serial", firstText(item, "serial", "serialNumber"));
        row.put("thumbnailImage", thumbnail);
        row.put("hasImage", !thumbnail.isBlank());
        return row;
    }

    static List<Map<String, Object>> extractObjekts(JsonNode root) {
        List<Map<String, Object>> grouped = extractCollectionGroups(root);
        if (!grouped.isEmpty()) return grouped;
        JsonNode array = firstArray(root);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (array == null) return rows;
        for (JsonNode node : array) {
            if (!node.isObject()) continue;
            JsonNode objekt = node.has("objekt") && node.get("objekt").isObject() ? node.get("objekt") : node;
            String collectionId = firstText(objekt, "collectionId", "collectionNo");
            String thumbnail = GravityService.safeImage(firstNonBlank(
                    firstText(objekt, "thumbnailImage", "frontImage"),
                    firstText(node, "thumbnailImage", "image")
            ));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("collectionId", collectionId);
            row.put("member", firstText(objekt, "member", "memberName"));
            row.put("season", firstText(objekt, "season", "class"));
            row.put("objektClass", firstText(objekt, "class", "objektClass"));
            row.put("tokenId", firstText(node, "tokenId", "objekt.tokenId"));
            row.put("objektNo", firstText(node, "objektNo", "objekt.objektNo"));
            row.put("serial", firstText(node, "serial", "serialNumber"));
            row.put("thumbnailImage", thumbnail);
            row.put("hasImage", !thumbnail.isBlank());
            if (!collectionId.isBlank() || !thumbnail.isBlank()) rows.add(row);
        }
        return rows;
    }

    static List<Map<String, Object>> extractScans(JsonNode root) {
        JsonNode array = firstArray(root);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (array == null) return rows;
        for (JsonNode node : array) {
            if (node.isTextual()) {
                rows.add(Map.of("serial", node.asText(), "when", "", "collectionId", ""));
                continue;
            }
            if (!node.isObject()) continue;
            String serial = firstText(node, "serial", "serialNumber", "code", "scannedSerial");
            if (serial.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("serial", serial);
            row.put("when", firstText(node, "scannedAt", "createdAt", "updatedAt"));
            row.put("collectionId", firstText(node, "collectionId", "objekt.collectionId"));
            rows.add(row);
        }
        return rows;
    }

    private static JsonNode firstArray(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) return null;
        if (root.isArray()) return root;
        for (String name : List.of("objekts", "tokens", "items", "results", "scans", "histories", "data", "profile")) {
            JsonNode child = root.get(name);
            if (child == null || child.isNull()) continue;
            if (child.isArray()) return child;
            if (child.isObject()) {
                JsonNode nested = firstArray(child);
                if (nested != null) return nested;
            }
        }
        return null;
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
                String text = current.asText("");
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static String shorten(String address) {
        if (address == null || address.length() < 12) return address == null ? "" : address;
        return address.substring(0, 6) + "…" + address.substring(address.length() - 4);
    }
}

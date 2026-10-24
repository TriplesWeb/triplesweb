package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
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
    private static final List<String> SCAN_PATHS = List.of(
            "/bff/v1/scan-histories",
            "/objekt/v1/scan-histories",
            "/bff/v1/users/me/scans"
    );

    private final GravityService gravity;

    public CosmoAccountService(GravityService gravity) {
        this.gravity = gravity;
    }

    public Map<String, Object> load() {
        JsonNode me = gravity.getAuthorized("/bff/v1/users/me");
        String nickname = firstText(me, "nickname", "profile.nickname");
        String address = firstText(me, "address", "profile.address");
        String image = GravityService.safeImage(firstText(me, "profileImageUrl", "profile.profileImageUrl"));

        JsonNode owned = address.isBlank()
                ? gravity.getAuthorized("/objekt/v1/owned-by/me?sort=newest")
                : gravity.getAuthorized("/objekt/v1/owned-by/" + UriUtils.encodePath(address, StandardCharsets.UTF_8) + "?sort=newest");
        List<Map<String, Object>> objekts = extractObjekts(owned);
        boolean ownedTruncated = objekts.size() > OWNED_LIMIT;
        if (ownedTruncated) objekts = objekts.subList(0, OWNED_LIMIT);

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
        view.put("ownedCount", objekts.size());
        view.put("ownedTruncated", ownedTruncated);
        view.put("ownedObjekts", objekts);
        view.put("hasOwned", !objekts.isEmpty());
        view.put("scans", scans);
        view.put("hasScans", !scans.isEmpty());
        view.put("scanCount", scans.size());
        return view;
    }

    static List<Map<String, Object>> extractObjekts(JsonNode root) {
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

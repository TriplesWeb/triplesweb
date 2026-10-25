package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Resolve a COSMO account and cache its indexed holdings, including collection serials. */
@Service
public class CosmoCollectionService {
    private static final int PAGE_SIZE = 24;
    private static final String NOT_FOUND = "없는 닉네임 입니다.";
    private final RestClient cosmo;
    private final RestClient index;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public CosmoCollectionService(
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String cosmoUrl,
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}") String indexUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        cosmo = RestClient.builder().baseUrl(cosmoUrl).requestFactory(factory).build();
        index = RestClient.builder().baseUrl(indexUrl).requestFactory(factory).build();
    }

    public Map<String, Object> search(String rawNickname, int page) {
        return search(rawNickname, page, "", "", "", "");
    }

    public Map<String, Object> search(String rawNickname, int page,
                                      String member, String season, String objektClass, String query) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        if (nickname.isBlank()) throw new IllegalArgumentException(NOT_FOUND);
        if (nickname.length() > 40 || nickname.contains("/") || nickname.contains(" "))
            throw new IllegalArgumentException("닉네임을 올바르게 입력해 주세요.");
        long now = Instant.now().getEpochSecond();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        Cached found = cache.compute(nickname.toLowerCase(Locale.ROOT), (key, current) -> {
            if (current != null && current.expiresAt > now) return current;
            return load(nickname);
        });
        if (found.address.isBlank()) throw new IllegalArgumentException(NOT_FOUND);
        String memberKey = member == null ? "" : member.trim();
        String seasonKey = season == null ? "" : season.trim();
        String classKey = objektClass == null ? "" : objektClass.trim();
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> filtered = found.cards.stream().filter(card -> {
            if (!memberKey.isBlank()
                    && !com.example.triplesweb.util.MemberPortraitOptions.sameMember(
                    text(card, "member"), memberKey)) return false;
            if (!seasonKey.isBlank() && !text(card, "season").equalsIgnoreCase(seasonKey)) return false;
            if (!classKey.isBlank() && !text(card, "objektClass").equalsIgnoreCase(classKey)) return false;
            if (!search.isBlank()) {
                String haystack = (text(card, "collectionId") + " " + text(card, "member") + " "
                        + text(card, "season") + " " + text(card, "objektClass") + " "
                        + text(card, "serial") + " " + text(card, "slug")).toLowerCase(Locale.ROOT);
                if (!haystack.contains(search)) return false;
            }
            return true;
        }).toList();
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        List<Map<String, Object>> cards = filtered.subList(from, Math.min(filtered.size(), from + PAGE_SIZE));
        String nickEnc = URLEncoder.encode(found.nickname, StandardCharsets.UTF_8);
        String filterQuery = filterQuery(memberKey, seasonKey, classKey, query == null ? "" : query.trim());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("nickname", found.nickname);
        view.put("nicknameEncoded", nickEnc);
        view.put("profileImageUrl", GravityService.safeImage(found.image));
        view.put("hasProfileImage", !GravityService.safeImage(found.image).isBlank());
        view.put("ownedCount", found.cards.size());
        view.put("filteredCount", filtered.size());
        view.put("cards", cards);
        view.put("allCards", found.cards);
        view.put("profileUrl", "/cosmo/profile/" + nickEnc.replace("+", "%20"));
        view.put("hasCards", !cards.isEmpty());
        view.put("pageDisplay", safePage + 1);
        view.put("totalPages", totalPages);
        view.put("hasPrev", safePage > 0);
        view.put("hasNext", safePage + 1 < totalPages);
        view.put("prevPage", Math.max(0, safePage - 1));
        view.put("nextPage", safePage + 1);
        view.put("selectedMember", memberKey);
        view.put("selectedSeason", seasonKey);
        view.put("selectedClass", classKey);
        view.put("searchQuery", query == null ? "" : query.trim());
        view.put("filterQuery", filterQuery);
        view.put("hasActiveFilters", !memberKey.isBlank() || !seasonKey.isBlank()
                || !classKey.isBlank() || !(query == null || query.isBlank()));
        return view;
    }

    private static String filterQuery(String member, String season, String klass, String query) {
        StringBuilder out = new StringBuilder();
        if (!member.isBlank()) out.append("&member=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        if (!season.isBlank()) out.append("&season=").append(URLEncoder.encode(season, StandardCharsets.UTF_8));
        if (!klass.isBlank()) out.append("&class=").append(URLEncoder.encode(klass, StandardCharsets.UTF_8));
        if (!query.isBlank()) out.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        return out.toString();
    }

    private static String text(Map<String, Object> card, String key) {
        Object value = card.get(key);
        return value == null ? "" : value.toString();
    }

    private Cached load(String nickname) {
        JsonNode profile;
        try {
            profile = cosmo.get().uri("/bff/v3/users/by-nickname/{nickname}", nickname)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) throw ex;
            return missing(nickname);
        }
        String address = profile == null ? "" : profile.path("address").asText("");
        if (address.isBlank()) return missing(nickname);
        if (!address.matches("0x[0-9a-fA-F]{40}")) throw new IllegalStateException("잘못된 계정 응답입니다.");
        List<Map<String, Object>> cards = new ArrayList<>();
        var seenTokens = new HashSet<String>();
        var seenCursors = new HashSet<String>();
        JsonNode cursor = null;
        do {
            JsonNode currentCursor = cursor;
            JsonNode response = index.get().uri(builder -> {
                builder.path("/objekts/owned-by/{address}");
                if (currentCursor != null) {
                    builder.queryParam("cursor[receivedAt]", currentCursor.path("receivedAt").asText());
                    builder.queryParam("cursor[id]", currentCursor.path("id").asText());
                }
                return builder.build(address.toLowerCase(Locale.ROOT));
            }).retrieve().body(JsonNode.class);
            if (response == null || !response.path("objekts").isArray())
                throw new IllegalStateException("보유 오브젝트 응답이 올바르지 않습니다.");
            for (JsonNode objekt : response.path("objekts")) {
                String token = objekt.path("tokenId").asText(objekt.path("id").asText());
                if (token.isBlank()) throw new IllegalStateException("잘못된 보유 오브젝트 응답입니다.");
                if (seenTokens.add(token)) cards.add(card(objekt));
            }
            cursor = response.get("nextCursor");
            if (cursor != null && !cursor.isNull()) {
                if (cursor.path("receivedAt").asText().isBlank() || cursor.path("id").asText().isBlank()
                        || !seenCursors.add(cursor.toString()))
                    throw new IllegalStateException("보유 목록의 다음 페이지를 불러오지 못했습니다.");
            }
        } while (cursor != null && !cursor.isNull());
        return new Cached(profile.path("nickname").asText(nickname), address,
                profile.path("profileImageUrl").asText(""), List.copyOf(cards), Instant.now().getEpochSecond() + 600);
    }

    private static Cached missing(String nickname) {
        return new Cached(nickname, "", "", List.of(), Instant.now().getEpochSecond() + 60);
    }

    private static Map<String, Object> card(JsonNode objekt) {
        Map<String, Object> card = new LinkedHashMap<>();
        String name = objekt.path("collectionId").asText("").replaceFirst("\\s*#\\d+\\s*$", "").trim();
        card.put("collectionId", name.isBlank() ? "오브젝트" : name);
        card.put("member", objekt.path("member").asText(""));
        card.put("season", objekt.path("season").asText(""));
        card.put("objektClass", objekt.path("class").asText(""));
        card.put("artist", objekt.path("artist").asText(""));
        card.put("slug", objekt.path("slug").asText(""));
        String image = GravityService.safeImage(objekt.path("frontImage").asText(""));
        if (image.isBlank()) image = GravityService.safeImage(objekt.path("thumbnailImage").asText(""));
        card.put("thumbnailImage", image);
        card.put("hasImage", !image.isBlank());
        long serial = objekt.path("serial").asLong(0);
        card.put("hasSerial", serial > 0);
        card.put("serial", serial > 0 ? Long.toString(serial) : "");
        String slug = objekt.path("slug").asText("");
        card.put("detailUrl", "/cosmo/objekt/detail/" + URLEncoder.encode(slug, StandardCharsets.UTF_8).replace("+", "%20"));
        card.put("hasDetail", !slug.isBlank());
        return Map.copyOf(card);
    }

    static List<Long> currentlyHeld(List<AbstractTransferLog> logs, String address) {
        String owner = address.toLowerCase(Locale.ROOT);
        List<AbstractTransferLog> ordered = new ArrayList<>(logs);
        ordered.sort(Comparator.comparingLong(AbstractTransferLog::blockNumber).thenComparingInt(AbstractTransferLog::logIndex));
        Map<Long, AbstractTransferLog> owned = new LinkedHashMap<>();
        for (AbstractTransferLog log : ordered) {
            if (owner.equals(log.toAddress())) owned.put(log.tokenId(), log);
            else if (owner.equals(log.fromAddress())) owned.remove(log.tokenId());
        }
        return owned.values().stream().sorted(Comparator.comparingLong(AbstractTransferLog::blockNumber)
                .thenComparingInt(AbstractTransferLog::logIndex).reversed()).map(AbstractTransferLog::tokenId).toList();
    }

    private record Cached(String nickname, String address, String image, List<Map<String, Object>> cards, long expiresAt) {}
}

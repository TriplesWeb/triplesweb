package com.example.triplesweb.service;

import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektSeasonCodes;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Resolve a COSMO account and cache its indexed holdings, including collection serials. */
@Service
public class CosmoCollectionService {
    private static final int PAGE_SIZE = 24;
    private static final long HOLDINGS_TTL_SECONDS = 600;
    private final java.time.Clock clock;
    private static final String NOT_FOUND = "없는 닉네임 입니다.";
    private final RestClient cosmo;
    private final RestClient index;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public CosmoCollectionService(
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String cosmoUrl,
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}") String indexUrl) {
        this(cosmoUrl, indexUrl, java.time.Clock.systemUTC());
    }

    CosmoCollectionService(String cosmoUrl, String indexUrl, java.time.Clock clock) {
        this.clock = clock;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        cosmo = RestClient.builder().baseUrl(cosmoUrl).requestFactory(factory).build();
        index = RestClient.builder().baseUrl(indexUrl).requestFactory(factory).build();
    }

    public Map<String, Object> search(String rawNickname, int page) {
        return search(rawNickname, page, "", "", "", "", "all");
    }

    public Map<String, Object> search(String rawNickname, int page,
                                      String member, String season, String objektClass, String query) {
        return search(rawNickname, page, member, season, objektClass, query, "all");
    }

    public Map<String, Object> search(String rawNickname, int page,
                                      String member, String season, String objektClass, String query, String group) {
        return search(rawNickname, page, member,
                season == null || season.isBlank() ? List.of() : List.of(season),
                objektClass == null || objektClass.isBlank() ? List.of() : List.of(objektClass),
                query, group);
    }

    public Map<String, Object> search(String rawNickname, int page,
                                      String member, List<String> seasons, List<String> classes,
                                      String query, String group) {
        return search(rawNickname, page, member, seasons, classes, query, group, "all", "latest");
    }

    public Map<String, Object> search(String rawNickname, int page,
                                      String member, List<String> seasons, List<String> classes,
                                      String query, String group, String kind, String sort) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        if (nickname.isBlank()) throw new IllegalArgumentException(NOT_FOUND);
        if (nickname.length() > 40 || nickname.contains("/") || nickname.contains(" "))
            throw new IllegalArgumentException("닉네임을 올바르게 입력해 주세요.");
        long now = clock.instant().getEpochSecond();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        Cached found = holdings(nickname, now);
        if (found.address.isBlank()) throw new IllegalArgumentException(NOT_FOUND);
        String memberKey = member == null ? "" : member.trim();
        List<String> seasonKeys = cleanSelections(seasons);
        List<String> classKeys = cleanSelections(classes);
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String selectedGroup = normalizeGroup(group, memberKey);
        List<String> selectedMembers = memberKey.isBlank() ? List.of() : List.of(memberKey);
        String kindKey = kind == null ? "" : kind.trim();
        boolean kindFiltered = !kindKey.isBlank() && !"all".equalsIgnoreCase(kindKey);
        String sortKey = sort == null || sort.isBlank() ? "latest" : sort.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> filtered = found.cards.stream().filter(card -> {
            if (!com.example.triplesweb.util.MemberPortraitOptions.matchesMemberGroup(
                    selectedGroup, selectedMembers, text(card, "member"))) return false;
            if (!matchesSelection(seasonKeys, text(card, "season"))) return false;
            if (!matchesSelection(classKeys, text(card, "objektClass"))) return false;
            if (kindFiltered && !kindKey.equalsIgnoreCase(text(card, "collectionNo"))) return false;
            if (!search.isBlank()) {
                String haystack = (text(card, "collectionId") + " " + text(card, "member") + " "
                        + text(card, "season") + " " + text(card, "objektClass") + " "
                        + text(card, "collectionNo") + " "
                        + text(card, "serial") + " " + text(card, "slug")).toLowerCase(Locale.ROOT);
                if (!ObjektSeasonCodes.matches(search, haystack, text(card, "season"), text(card, "collectionNo"))) {
                    return false;
                }
            }
            return true;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        filtered.sort(sortComparator(sortKey));
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        List<Map<String, Object>> cards = filtered.subList(from, Math.min(filtered.size(), from + PAGE_SIZE));
        String nickEnc = URLEncoder.encode(found.nickname, StandardCharsets.UTF_8);
        String filterQuery = filterQuery(memberKey, seasonKeys, classKeys, query == null ? "" : query.trim(), selectedGroup, kindKey, sortKey);
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
        view.put("page", safePage);
        view.put("pageDisplay", safePage + 1);
        view.put("pageSize", PAGE_SIZE);
        view.put("loadedCount", cards.size());
        view.put("totalPages", totalPages);
        view.put("hasPrev", safePage > 0);
        boolean hasMore = safePage + 1 < totalPages;
        view.put("hasNext", hasMore);
        view.put("hasMore", hasMore);
        view.put("prevPage", Math.max(0, safePage - 1));
        view.put("nextPage", safePage + 1);
        view.put("selectedMember", "other".equals(selectedGroup) ? "" : memberKey);
        view.put("selectedSeasons", seasonKeys);
        view.put("selectedClasses", classKeys);
        view.put("selectedSeason", seasonKeys.size() == 1 ? seasonKeys.get(0) : "");
        view.put("selectedClass", classKeys.size() == 1 ? classKeys.get(0) : "");
        view.put("selectedGroup", selectedGroup);
        view.put("isOtherGroup", "other".equals(selectedGroup));
        view.put("isMembersGroup", "members".equals(selectedGroup));
        view.put("isAllGroup", "all".equals(selectedGroup));
        view.put("searchQuery", query == null ? "" : query.trim());
        view.put("selectedKind", kindFiltered ? kindKey : "all");
        view.put("selectedSort", sortKey);
        view.put("kindOptions", found.cards.stream().map(card -> text(card, "collectionNo"))
                .filter(value -> !value.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        view.put("filterQuery", filterQuery);
        view.put("hasActiveFilters", !"all".equals(selectedGroup) || !memberKey.isBlank() || !seasonKeys.isEmpty()
                || !classKeys.isEmpty() || kindFiltered || !"latest".equals(sortKey)
                || !(query == null || query.isBlank()));
        return view;
    }

    private static Comparator<Map<String, Object>> sortComparator(String sort) {
        Comparator<Map<String, Object>> byReceived = Comparator.comparingInt(card -> {
            Object value = card.get("receivedIndex");
            return value instanceof Number number ? number.intValue() : 0;
        });
        Comparator<Map<String, Object>> byNumber = Comparator
                .comparingLong((Map<String, Object> card) -> leadingNumber(text(card, "collectionNo")))
                .thenComparing(card -> text(card, "collectionNo"), String.CASE_INSENSITIVE_ORDER);
        Comparator<Map<String, Object>> byMemberOrder = Comparator
                .comparingInt((Map<String, Object> card) -> MemberPortraitOptions.objektMembers(text(card, "member"))
                        .stream().mapToInt(MemberPortraitOptions::memberIndex).min().orElse(24))
                .thenComparing(card -> text(card, "member"), String.CASE_INSENSITIVE_ORDER);
        Comparator<Map<String, Object>> byMint = Comparator
                .comparingLong((Map<String, Object> card) -> {
                    Object mint = card.get("mintCount");
                    if (mint instanceof Number number) return number.longValue();
                    return leadingNumber(text(card, "collectionNo"));
                })
                .thenComparing(byNumber);
        return switch (sort) {
            case "oldest" -> byReceived.reversed();
            case "number_low" -> byNumber;
            case "number_high" -> byNumber.reversed();
            case "first_member" -> byMemberOrder;
            case "last_member" -> byMemberOrder.reversed();
            case "mint_low" -> byMint;
            case "mint_high" -> byMint.reversed();
            default -> byReceived;
        };
    }

    /** 동일 닉네임 동시 miss 시 한 번만 load 한다. */
    private Cached holdings(String nickname, long now) {
        String key = nickname.toLowerCase(Locale.ROOT);
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) return cached;
        Object lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            Cached again = cache.get(key);
            if (again != null && again.expiresAt > now) return again;
            Cached loaded = load(nickname);
            cache.put(key, loaded);
            return loaded;
        }
    }

    private static long leadingNumber(String value) {
        var matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(value == null ? "" : value);
        if (!matcher.find()) return Long.MAX_VALUE;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ex) { return Long.MAX_VALUE; }
    }

    private static String normalizeGroup(String group, String member) {
        if ("other".equalsIgnoreCase(group)) return "other";
        if ("members".equalsIgnoreCase(group) || (member != null && !member.isBlank())) return "members";
        return "all";
    }

    private static String filterQuery(String member, List<String> seasons, List<String> classes, String query,
                                      String group, String kind, String sort) {
        StringBuilder out = new StringBuilder();
        if (group != null && !group.isBlank() && !"all".equals(group))
            out.append("&group=").append(URLEncoder.encode(group, StandardCharsets.UTF_8));
        if (!member.isBlank() && !"other".equals(group))
            out.append("&member=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        for (String season : seasons)
            out.append("&season=").append(URLEncoder.encode(season, StandardCharsets.UTF_8));
        for (String klass : classes)
            out.append("&class=").append(URLEncoder.encode(klass, StandardCharsets.UTF_8));
        if (kind != null && !kind.isBlank() && !"all".equalsIgnoreCase(kind))
            out.append("&kind=").append(URLEncoder.encode(kind, StandardCharsets.UTF_8));
        if (sort != null && !sort.isBlank() && !"latest".equalsIgnoreCase(sort))
            out.append("&sort=").append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
        if (!query.isBlank()) out.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        return out.toString();
    }

    private static List<String> cleanSelections(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static boolean matchesSelection(List<String> selected, String value) {
        if (selected == null || selected.isEmpty()) return true;
        return selected.stream().anyMatch(item -> item.equalsIgnoreCase(value));
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
        int pages = 0;
        do {
            if (++pages > 1000) throw new IllegalStateException("보유 목록이 너무 큽니다. 잠시 후 다시 조회해 주세요.");
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
                if (seenTokens.add(token)) cards.add(card(objekt, cards.size()));
            }
            cursor = response.get("nextCursor");
            if (cursor != null && !cursor.isNull()) {
                if (cursor.path("receivedAt").asText().isBlank() || cursor.path("id").asText().isBlank()
                        || !seenCursors.add(cursor.toString()))
                    throw new IllegalStateException("보유 목록의 다음 페이지를 불러오지 못했습니다.");
            }
        } while (cursor != null && !cursor.isNull());
        return new Cached(profile.path("nickname").asText(nickname), address,
                profile.path("profileImageUrl").asText(""), List.copyOf(cards), clock.instant().getEpochSecond() + HOLDINGS_TTL_SECONDS);
    }

    private Cached missing(String nickname) {
        return new Cached(nickname, "", "", List.of(), clock.instant().getEpochSecond() + 60);
    }

    private static String collectionNo(JsonNode objekt, String collectionId) {
        String explicit = objekt.path("collectionNo").asText("");
        if (!explicit.isBlank()) return explicit.trim();
        String[] parts = collectionId == null ? new String[0] : collectionId.trim().split("\\s+");
        if (parts.length == 0) return "";
        String last = parts[parts.length - 1];
        return last.chars().anyMatch(Character::isDigit) ? last : "";
    }

    private static Map<String, Object> card(JsonNode objekt, int receivedIndex) {
        Map<String, Object> card = new LinkedHashMap<>();
        String name = objekt.path("collectionId").asText("").replaceFirst("\\s*#\\d+\\s*$", "").trim();
        card.put("collectionId", name.isBlank() ? "오브젝트" : name);
        card.put("member", MemberPortraitOptions.displayMember(objekt.path("member").asText("")));
        card.put("season", objekt.path("season").asText(""));
        card.put("objektClass", objekt.path("class").asText(""));
        card.put("collectionNo", collectionNo(objekt, name));
        card.put("receivedIndex", receivedIndex);
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


package com.example.triplesweb.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오브젝트 상세 설명 키.
 * 같은 시즌·번호는 {@code group-} 키를 공유하고,
 * JSON에 있는 묶음과 동기화가 기록한 묶음은 {@code member-} 키를 쓴다.
 */
public final class ObjektDescriptionKeys {
    private static final List<MemberText> MEMBERS = load();
    private static final Set<String> EXCEPTIONS = exceptions(MEMBERS);
    private static final Set<String> RUNTIME = ConcurrentHashMap.newKeySet();

    private ObjektDescriptionKeys() {}

    public record MemberText(String season, String collectionNo, String member, String slug,
                             String collectionId, String description) {}

    public static List<MemberText> members() {
        return MEMBERS;
    }

    public static boolean memberSpecific(String season, String collectionNo) {
        String key = pair(season, collectionNo);
        return EXCEPTIONS.contains(key) || RUNTIME.contains(key);
    }

    /** 같은 시즌·번호의 문장이 갈릴 때 호출한다. 재시작 뒤에는 DB에서 다시 채운다. */
    public static void markMemberSpecific(String season, String collectionNo) {
        String key = pair(season, collectionNo);
        if (key.startsWith("|") || key.endsWith("|")) return;
        RUNTIME.add(key);
    }

    /** JSON 예외가 아닌 런타임 묶음만 다시 공통 키로 돌린다. */
    public static void clearMemberSpecific(String season, String collectionNo) {
        RUNTIME.remove(pair(season, collectionNo));
    }

    public static boolean jsonException(String season, String collectionNo) {
        return EXCEPTIONS.contains(pair(season, collectionNo));
    }

    public static String season(Map<String, Object> item) {
        return text(item, "season");
    }

    public static String collectionNo(Map<String, Object> item) {
        return text(item, "collectionNo", "collectionNumber");
    }

    public static boolean canGroup(Map<String, Object> item) {
        return !season(item).isEmpty() && !collectionNo(item).isEmpty();
    }

    public static void clearRuntime() {
        RUNTIME.clear();
    }

    /** 저장에 쓰는 키. 예외가 아니면 시즌·번호 공통 키, 예외이면 멤버 키. */
    public static String storageKey(Map<String, Object> item) {
        String season = text(item, "season");
        String number = text(item, "collectionNo", "collectionNumber");
        if (season.isEmpty() || number.isEmpty()) return ObjektLinks.id(item);
        String seasonKey = season.toLowerCase(Locale.ROOT);
        String numberKey = number.toLowerCase(Locale.ROOT);
        if (memberSpecific(seasonKey, numberKey)) {
            String member = memberOf(item);
            if (!member.isEmpty()) return memberKey(seasonKey, numberKey, member);
        }
        return groupKey(seasonKey, numberKey);
    }

    public static String groupKey(String season, String collectionNo) {
        String seasonKey = normalize(season);
        String numberKey = normalize(collectionNo);
        if (seasonKey.isEmpty() || numberKey.isEmpty()) return "";
        return named("group-", seasonKey.length() + ":" + seasonKey + ":" + numberKey);
    }

    public static String memberKey(String season, String collectionNo, String member) {
        String seasonKey = normalize(season);
        String numberKey = normalize(collectionNo);
        String memberKey = memberToken(member);
        if (seasonKey.isEmpty() || numberKey.isEmpty() || memberKey.isEmpty()) return "";
        String identity = seasonKey.length() + ":" + seasonKey + ":" + numberKey + ":" + memberKey;
        return named("member-", identity);
    }

    /**
     * 조회 순서. 예외 카드는 공통 {@code group-} 키를 보지 않는다.
     * 그 외에는 저장 키 다음으로 id, 슬러그, 컬렉션 ID를 본다.
     */
    public static List<String> readKeys(Map<String, Object> item, String requested) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String storage = storageKey(item);
        add(keys, storage);
        if (storage.startsWith("member-")) {
            add(keys, text(item, "slug"));
            add(keys, text(item, "collectionId"));
            add(keys, ObjektLinks.id(item));
        } else {
            add(keys, ObjektLinks.id(item));
            add(keys, text(item, "slug"));
            add(keys, text(item, "collectionId"));
        }
        add(keys, requested);
        return List.copyOf(keys);
    }

    private static void add(Set<String> keys, String value) {
        if (value != null && !value.isBlank()) keys.add(value);
    }

    private static String named(String prefix, String identity) {
        return prefix + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static String pair(String season, String collectionNo) {
        return normalize(season) + "|" + normalize(collectionNo);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String memberToken(String value) {
        return normalize(value).replaceAll("[^a-z0-9]", "");
    }

    /** 화면 이름은 한글이라 키가 비면 슬러그 {@code season-member-number}에서 멤버를 꺼낸다. */
    private static String memberOf(Map<String, Object> item) {
        String named = memberToken(text(item, "member", "memberName"));
        if (!named.isEmpty()) return named;
        String slug = normalize(text(item, "slug"));
        String season = normalize(text(item, "season")).replaceAll("[^a-z0-9]", "");
        String number = normalize(text(item, "collectionNo", "collectionNumber")).replaceAll("[^a-z0-9]", "");
        String prefix = season + "-";
        String suffix = "-" + number;
        if (season.isEmpty() || number.isEmpty() || !slug.startsWith(prefix) || !slug.endsWith(suffix)) return "";
        if (slug.length() <= prefix.length() + suffix.length()) return "";
        return memberToken(slug.substring(prefix.length(), slug.length() - suffix.length()));
    }

    private static String text(Map<String, Object> item, String... keys) {
        if (item == null) return "";
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return "";
    }

    private static Set<String> exceptions(List<MemberText> members) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (MemberText member : members) keys.add(pair(member.season(), member.collectionNo()));
        return Set.copyOf(keys);
    }

    private static List<MemberText> load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ObjektDescriptionKeys.class.getResourceAsStream("/data/objekt-member-descriptions.json")) {
            if (in == null) return List.of();
            File file = mapper.readValue(in, File.class);
            if (file.groups == null) return List.of();
            List<MemberText> rows = new ArrayList<>();
            for (Group group : file.groups) {
                if (group == null || group.members == null) continue;
                for (Member member : group.members) {
                    if (member == null) continue;
                    rows.add(new MemberText(
                            group.season == null ? "" : group.season,
                            group.collectionNo == null ? "" : group.collectionNo,
                            member.member == null ? "" : member.member,
                            member.slug == null ? "" : member.slug,
                            member.collectionId == null ? "" : member.collectionId,
                            member.description == null ? "" : member.description));
                }
            }
            return List.copyOf(rows);
        } catch (IOException error) {
            throw new IllegalStateException("멤버별 오브젝트 설명을 읽지 못했습니다.", error);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class File {
        public List<Group> groups;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Group {
        public String season;
        public String collectionNo;
        public List<Member> members;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Member {
        public String member;
        public String slug;
        public String collectionId;
        public String description;
    }
}

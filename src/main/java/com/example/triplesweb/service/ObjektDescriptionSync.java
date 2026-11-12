package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * 설명이 없는 카드의 Apollo {@code data.description}을 읽어
 * 같은 시즌·번호면 공통 키, 문장이 갈리면 멤버 키에 넣는다.
 */
@Slf4j
@Service
public class ObjektDescriptionSync {
    static final int BUDGET = 24;
    private static final Duration EMPTY_COOLDOWN = Duration.ofHours(6);
    private static final Duration FAIL_COOLDOWN = Duration.ofMinutes(30);

    private final ObjektService objekts;
    private final ObjektDescriptionRepository descriptions;
    private final ObjektDescriptionScopeRepository scopes;
    private final ApolloObjektMetadataClient apollo;
    private final ObjektStoryTranslateService translate;
    private final boolean enabled;

    private final Map<String, String> knownEnglish = new LinkedHashMap<>();
    private final Map<String, Long> retryAt = new LinkedHashMap<>();
    private final Set<String> matched = new LinkedHashSet<>();
    private final Set<String> promoting = new LinkedHashSet<>();

    public ObjektDescriptionSync(ObjektService objekts,
                                 ObjektDescriptionRepository descriptions,
                                 ObjektDescriptionScopeRepository scopes,
                                 ApolloObjektMetadataClient apollo,
                                 ObjektStoryTranslateService translate,
                                 @Value("${objekt.description-sync.enabled:true}") boolean enabled) {
        this.objekts = objekts;
        this.descriptions = descriptions;
        this.scopes = scopes;
        this.apollo = apollo;
        this.translate = translate;
        this.enabled = enabled;
    }

    @PostConstruct
    void loadScopes() {
        for (ObjektDescriptionScope row : scopes.findAll()) {
            if (row.getId() == null) continue;
            ObjektDescriptionKeys.markMemberSpecific(row.getId().getSeason(), row.getId().getCollectionNo());
        }
    }

    @Scheduled(fixedDelayString = "${objekt.description-sync.ms:300000}",
            initialDelayString = "${objekt.description-sync.initial-ms:30000}")
    public void scheduled() {
        if (!enabled) return;
        try {
            sync(objekts.findCollections("triples"));
        } catch (RuntimeException ex) {
            log.warn("오브젝트 설명 동기화 실패: {}", ex.toString());
        }
    }

    public int sync(List<Map<String, Object>> catalog) {
        return sync(catalog, slug -> apollo.collectionDescription(slug).orElse(null), translate::sentence);
    }

    int sync(List<Map<String, Object>> catalog, UnaryOperator<String> apolloFetch, UnaryOperator<String> toKorean) {
        expireRetries();
        Map<String, List<Card>> groups = groups(catalog);
        int budget = BUDGET;
        int inserted = 0;
        for (int pass = 0; pass < 4; pass++) {
            List<String> queue = queue(groups);
            for (String slug : queue) {
                if (budget == 0) break;
                budget--;
                String english;
                try {
                    english = apolloFetch.apply(slug);
                } catch (RuntimeException ex) {
                    english = null;
                }
                if (english == null) {
                    retryAt.put(slug, System.currentTimeMillis() + FAIL_COOLDOWN.toMillis());
                    continue;
                }
                knownEnglish.put(slug, english.trim());
                if (english.isBlank()) retryAt.put(slug, System.currentTimeMillis() + EMPTY_COOLDOWN.toMillis());
            }
            inserted += classify(groups, toKorean);
            if (queue.isEmpty() || budget == 0) break;
        }
        if (inserted > 0) log.info("오브젝트 설명 {}건을 분류해 저장했습니다.", inserted);
        return inserted;
    }

    private void expireRetries() {
        long now = System.currentTimeMillis();
        retryAt.entrySet().removeIf(entry -> entry.getValue() <= now);
        knownEnglish.entrySet().removeIf(entry -> entry.getValue().isBlank() && !retryAt.containsKey(entry.getKey()));
    }

    private int classify(Map<String, List<Card>> groups, UnaryOperator<String> toKorean) {
        int inserted = 0;
        for (List<Card> group : groups.values()) {
            if (group.isEmpty()) continue;
            Card first = group.get(0);
            String pair = first.season + "|" + first.number;
            if (promoting.contains(pair)) {
                inserted += finishPromotion(group, toKorean);
                continue;
            }
            if (ObjektDescriptionKeys.memberSpecific(first.season, first.number)) {
                inserted += fillMembers(group, toKorean);
                continue;
            }
            if (!hasText(ObjektDescriptionKeys.groupKey(first.season, first.number))) {
                inserted += classifyNewGroup(group, toKorean);
            } else {
                inserted += classifyAddition(group, toKorean);
            }
        }
        return inserted;
    }

    private int classifyNewGroup(List<Card> group, UnaryOperator<String> toKorean) {
        List<Card> open = group.stream().filter(card -> !hasOwnText(card)).toList();
        if (open.isEmpty() || open.stream().anyMatch(card -> !knownEnglish.containsKey(card.slug))) return 0;
        Set<String> texts = new LinkedHashSet<>();
        for (Card card : open) texts.add(knownEnglish.get(card.slug));
        boolean anyBlank = texts.contains("");
        Set<String> filled = new LinkedHashSet<>(texts);
        filled.remove("");
        if (filled.isEmpty()) return 0;
        if (anyBlank || filled.size() > 1 || group.stream().anyMatch(this::hasOwnText)) {
            promoting.add(group.get(0).season + "|" + group.get(0).number);
            return finishPromotion(group, toKorean);
        }
        String korean = korean(filled.iterator().next(), toKorean);
        if (korean.isBlank()) return 0;
        return insertIfAbsent(ObjektDescriptionKeys.groupKey(group.get(0).season, group.get(0).number), korean) ? 1 : 0;
    }

    private int classifyAddition(List<Card> group, UnaryOperator<String> toKorean) {
        List<Card> open = group.stream().filter(card -> candidate(card, true)).toList();
        if (open.isEmpty() || open.stream().anyMatch(card -> !knownEnglish.containsKey(card.slug))) return 0;
        String baseline = group.stream()
                .filter(card -> !candidate(card, true) && knownEnglish.containsKey(card.slug))
                .map(card -> knownEnglish.get(card.slug))
                .findFirst()
                .orElse(null);
        if (baseline == null) return 0;
        boolean same = open.stream().allMatch(card -> baseline.equals(knownEnglish.get(card.slug)) && !baseline.isBlank());
        if (same) {
            open.forEach(card -> matched.add(card.slug));
            return 0;
        }
        promoting.add(group.get(0).season + "|" + group.get(0).number);
        return finishPromotion(group, toKorean);
    }

    private int finishPromotion(List<Card> group, UnaryOperator<String> toKorean) {
        boolean ready = group.stream().allMatch(card -> knownEnglish.containsKey(card.slug) || hasOwnText(card));
        if (!ready) return 0;
        Map<Card, String> pending = new LinkedHashMap<>();
        for (Card card : group) {
            if (hasOwnText(card)) continue;
            String english = knownEnglish.getOrDefault(card.slug, "");
            if (english.isBlank()) continue;
            String korean = korean(english, toKorean);
            if (korean.isBlank()) return 0;
            pending.put(card, korean);
        }
        int inserted = 0;
        for (Map.Entry<Card, String> entry : pending.entrySet()) {
            Card card = entry.getKey();
            if (insertIfAbsent(ObjektDescriptionKeys.memberKey(card.season, card.number, card.member), entry.getValue())) inserted++;
        }
        rememberScope(group.get(0).season, group.get(0).number);
        promoting.remove(group.get(0).season + "|" + group.get(0).number);
        group.forEach(card -> matched.add(card.slug));
        return inserted;
    }

    private int fillMembers(List<Card> group, UnaryOperator<String> toKorean) {
        int inserted = 0;
        for (Card card : group) {
            if (!knownEnglish.containsKey(card.slug) || hasOwnText(card)) continue;
            String english = knownEnglish.get(card.slug);
            if (english.isBlank()) continue;
            String korean = korean(english, toKorean);
            if (korean.isBlank()) continue;
            if (insertIfAbsent(ObjektDescriptionKeys.memberKey(card.season, card.number, card.member), korean)) inserted++;
        }
        return inserted;
    }

    private String korean(String english, UnaryOperator<String> toKorean) {
        String translated;
        try {
            translated = toKorean.apply(english);
        } catch (RuntimeException ex) {
            return "";
        }
        if (translated == null || !ObjektStoryTranslateService.looksKorean(translated)) return "";
        return translated.trim();
    }

    private void rememberScope(String season, String number) {
        ObjektDescriptionKeys.markMemberSpecific(season, number);
        ObjektDescriptionScope.Key id = new ObjektDescriptionScope.Key(season, number);
        if (scopes.existsById(id)) return;
        ObjektDescriptionScope row = new ObjektDescriptionScope();
        row.setId(id);
        scopes.save(row);
    }

    private List<String> queue(Map<String, List<Card>> groups) {
        LinkedHashSet<String> slugs = new LinkedHashSet<>();
        for (List<Card> group : groups.values()) {
            if (!promoting.contains(group.get(0).season + "|" + group.get(0).number)) continue;
            for (Card card : group) {
                if (!knownEnglish.containsKey(card.slug) && !hasOwnText(card)) slugs.add(card.slug);
            }
        }
        for (List<Card> group : groups.values()) {
            boolean sharedText = hasText(ObjektDescriptionKeys.groupKey(group.get(0).season, group.get(0).number));
            for (Card card : group) {
                if (candidate(card, sharedText)) slugs.add(card.slug);
            }
        }
        for (List<Card> group : groups.values()) {
            Card first = group.get(0);
            if (ObjektDescriptionKeys.memberSpecific(first.season, first.number)) continue;
            if (promoting.contains(first.season + "|" + first.number)) continue;
            if (!hasText(ObjektDescriptionKeys.groupKey(first.season, first.number))) continue;
            boolean candidateKnown = group.stream().anyMatch(card -> candidate(card, true) && knownEnglish.containsKey(card.slug));
            boolean siblingKnown = group.stream().anyMatch(card -> !candidate(card, true) && knownEnglish.containsKey(card.slug));
            if (!candidateKnown || siblingKnown) continue;
            group.stream().filter(card -> !candidate(card, true)).map(card -> card.slug).findFirst().ifPresent(slugs::add);
        }
        slugs.removeIf(slug -> knownEnglish.containsKey(slug) || coolingDown(slug));
        return new ArrayList<>(slugs);
    }

    private boolean candidate(Card card, boolean groupHasText) {
        if (card.slug.isBlank() || matched.contains(card.slug) || hasOwnText(card)) return false;
        if (ObjektDescriptionKeys.memberSpecific(card.season, card.number) || !groupHasText) return true;
        Instant created = parseInstant(card.createdAt);
        LocalDateTime watermark = updatedAt(ObjektDescriptionKeys.groupKey(card.season, card.number));
        return created != null && watermark != null && created.isAfter(watermark.atZone(ZoneId.systemDefault()).toInstant());
    }

    private boolean hasOwnText(Card card) {
        return hasText(ObjektDescriptionKeys.memberKey(card.season, card.number, card.member))
                || hasText(card.slug)
                || hasText(card.collectionId);
    }

    private boolean hasText(String key) {
        if (key == null || key.isBlank()) return false;
        return descriptions.findByObjektKey(key)
                .map(ObjektDescription::getDescription)
                .filter(text -> text != null && !text.isBlank())
                .isPresent();
    }

    private LocalDateTime updatedAt(String key) {
        return descriptions.findByObjektKey(key).map(ObjektDescription::getUpdatedAt).orElse(null);
    }

    private boolean insertIfAbsent(String key, String text) {
        if (key == null || key.isBlank() || text == null || text.isBlank()) return false;
        if (descriptions.findByObjektKey(key).isPresent()) return false;
        ObjektDescription row = new ObjektDescription();
        row.setObjektKey(key);
        row.setDescription(text.trim());
        descriptions.save(row);
        return true;
    }

    private boolean coolingDown(String slug) {
        Long until = retryAt.get(slug);
        return until != null && until > System.currentTimeMillis() && !knownEnglish.containsKey(slug);
    }

    private Map<String, List<Card>> groups(List<Map<String, Object>> catalog) {
        Map<String, List<Card>> groups = new LinkedHashMap<>();
        if (catalog == null) return groups;
        for (Map<String, Object> item : catalog) {
            String season = text(item, "season").toLowerCase(Locale.ROOT);
            String number = text(item, "collectionNo", "collectionNumber").toLowerCase(Locale.ROOT);
            String slug = text(item, "slug").toLowerCase(Locale.ROOT);
            if (season.isBlank() || number.isBlank() || slug.isBlank()) continue;
            String memberToken = slugMember(season, number, slug);
            if (memberToken.isBlank()) memberToken = token(text(item, "member", "memberName"));
            groups.computeIfAbsent(season + "|" + number, key -> new ArrayList<>())
                    .add(new Card(season, number, memberToken, slug, text(item, "collectionId"), text(item, "createdAt")));
        }
        return groups;
    }

    private static String slugMember(String season, String number, String slug) {
        String seasonKey = season.replaceAll("[^a-z0-9]", "");
        String numberKey = number.replaceAll("[^a-z0-9]", "");
        String prefix = seasonKey + "-";
        String suffix = "-" + numberKey;
        if (!slug.startsWith(prefix) || !slug.endsWith(suffix) || slug.length() <= prefix.length() + suffix.length()) return "";
        return slug.substring(prefix.length(), slug.length() - suffix.length()).replaceAll("[^a-z0-9]", "");
    }

    private static String token(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String text(Map<String, Object> item, String... keys) {
        if (item == null) return "";
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return "";
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // 다음 형식을 본다.
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // 다음 형식을 본다.
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private record Card(String season, String number, String member, String slug, String collectionId, String createdAt) {}
}

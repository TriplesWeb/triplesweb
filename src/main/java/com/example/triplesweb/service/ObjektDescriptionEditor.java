package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.entity.ObjektDescriptionSharePref;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.repository.ObjektDescriptionSharePrefRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import com.example.triplesweb.util.TripleSMembers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 관리자 설명 저장.
 * 같은 시즌 안에서 고른 멤버·번호에 동시에 적용한다.
 * 멤버를 일부만 고르면 해당 번호는 멤버별로 분리되고, 전원 고르면 번호 공통으로 둔다.
 */
@Service
public class ObjektDescriptionEditor {
    private final ObjektService objekts;
    private final ObjektDescriptionRepository descriptions;
    private final ObjektDescriptionScopeRepository scopes;
    private final ObjektDescriptionSharePrefRepository sharePrefs;

    public ObjektDescriptionEditor(ObjektService objekts,
                                   ObjektDescriptionRepository descriptions,
                                   ObjektDescriptionScopeRepository scopes,
                                   ObjektDescriptionSharePrefRepository sharePrefs) {
        this.objekts = objekts;
        this.descriptions = descriptions;
        this.scopes = scopes;
        this.sharePrefs = sharePrefs;
    }

    public Map<String, Object> view(Map<String, Object> item) {
        return view(item, true);
    }

    public Map<String, Object> view(Map<String, Object> item, boolean includeShareOptions) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean canGroup = ObjektDescriptionKeys.canGroup(item);
        String season = ObjektDescriptionKeys.season(item);
        String number = ObjektDescriptionKeys.collectionNo(item);
        boolean member = canGroup && ObjektDescriptionKeys.memberSpecific(season, number);
        int count = canGroup ? Math.max(1, objekts.groupCount(item)) : 1;
        List<Map<String, Object>> shareMembers = List.of();
        List<String> seasonNumbers = List.of();
        List<String> selectedMembers = List.of();
        List<String> selectedNumbers = List.of();
        SharePref pref = canGroup ? loadSharePref(season, number) : SharePref.EMPTY;
        if (canGroup && includeShareOptions) {
            shareMembers = shareMembers(item, pref);
            seasonNumbers = seasonNumbers(season, number);
            selectedNumbers = selectedNumbers(seasonNumbers, number, pref);
            selectedMembers = selectedMembers(shareMembers, ObjektDescriptionKeys.memberOf(item), pref);
        } else if (canGroup) {
            String currentMember = ObjektDescriptionKeys.memberOf(item);
            LinkedHashSet<String> members = new LinkedHashSet<>(pref.members());
            if (!currentMember.isEmpty()) members.add(currentMember);
            selectedMembers = List.copyOf(members);
            LinkedHashSet<String> numbers = new LinkedHashSet<>(pref.numbers());
            if (!number.isBlank()) numbers.add(number);
            selectedNumbers = List.copyOf(numbers);
        }
        body.put("descriptionScope", !canGroup ? "single" : member ? "member" : "group");
        body.put("descriptionGroupLabel", canGroup ? (season + " " + number).trim() : "");
        body.put("descriptionGroupCount", count);
        body.put("descriptionMember", ObjektDescriptionKeys.memberOf(item));
        body.put("descriptionNumber", number);
        body.put("descriptionSeason", season);
        body.put("descriptionShareMembers", shareMembers);
        body.put("descriptionSeasonNumbers", seasonNumbers);
        body.put("descriptionSelectedMembers", selectedMembers);
        body.put("descriptionSelectedNumbers", selectedNumbers);
        return body;
    }

    @Transactional
    public String save(Map<String, Object> item, String description, String requestedScope) {
        return save(item, description, requestedScope, null, null);
    }

    @Transactional
    public String save(Map<String, Object> item, String description, String requestedScope,
                       Collection<String> members, Collection<String> numbers) {
        String text = description == null ? "" : description.trim();
        if (!ObjektDescriptionKeys.canGroup(item)) {
            flushWrites(Map.of(ObjektDescriptionKeys.storageKey(item), text), Map.of());
            return text;
        }
        String season = ObjektDescriptionKeys.season(item);
        String currentNumber = ObjektDescriptionKeys.collectionNo(item);
        String currentMember = ObjektDescriptionKeys.memberOf(item);
        Set<String> targetNumbers = resolveNumbers(season, currentNumber, numbers);
        Set<String> targetMembers = resolveMembers(item, currentMember, members, requestedScope);

        Map<String, List<Map<String, Object>>> cardsByNumber = new LinkedHashMap<>();
        LinkedHashSet<String> prefetchKeys = new LinkedHashSet<>();
        for (String number : targetNumbers) {
            List<Map<String, Object>> cards = siblings(season, number);
            cardsByNumber.put(number, cards);
            prefetchKeys.add(ObjektDescriptionKeys.groupKey(season, number));
            for (Map<String, Object> card : cards) {
                prefetchKeys.addAll(ObjektDescriptionKeys.readKeys(card, ""));
                String token = ObjektDescriptionKeys.memberOf(card);
                if (!token.isEmpty()) {
                    prefetchKeys.add(ObjektDescriptionKeys.memberKey(season, number, token));
                }
            }
        }
        Map<String, ObjektDescription> existing = loadEntities(prefetchKeys);

        LinkedHashMap<String, String> writes = new LinkedHashMap<>();
        LinkedHashSet<ObjektDescriptionScope.Key> remember = new LinkedHashSet<>();
        LinkedHashSet<ObjektDescriptionScope.Key> forget = new LinkedHashSet<>();
        for (String number : targetNumbers) {
            planNumber(season, number, targetMembers, text, cardsByNumber.getOrDefault(number, List.of()),
                    existing, writes, remember, forget);
        }
        flushWrites(writes, existing);
        flushScopes(remember, forget);
        flushSharePrefs(season, targetNumbers, targetMembers, targetNumbers);
        return text;
    }

    private void planNumber(String season, String number, Set<String> selectedMembers, String text,
                            List<Map<String, Object>> cards, Map<String, ObjektDescription> existing,
                            Map<String, String> writes,
                            Set<ObjektDescriptionScope.Key> remember,
                            Set<ObjektDescriptionScope.Key> forget) {
        LinkedHashSet<String> present = new LinkedHashSet<>();
        for (Map<String, Object> card : cards) {
            String token = ObjektDescriptionKeys.memberOf(card);
            if (!token.isEmpty()) present.add(token);
        }
        String groupKey = ObjektDescriptionKeys.groupKey(season, number);
        ObjektDescriptionScope.Key scopeKey = scopeKey(season, number);

        if (selectedMembers.isEmpty() && present.isEmpty()) {
            writes.put(groupKey, text);
            if (!ObjektDescriptionKeys.jsonException(season, number)) {
                ObjektDescriptionKeys.clearMemberSpecific(season, number);
                forget.add(scopeKey);
            }
            return;
        }

        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (String token : selectedMembers) {
            if (present.isEmpty() || present.contains(token)) targets.add(token);
        }
        if (targets.isEmpty() && present.isEmpty()) {
            writes.put(groupKey, text);
            if (!ObjektDescriptionKeys.jsonException(season, number)) {
                ObjektDescriptionKeys.clearMemberSpecific(season, number);
                forget.add(scopeKey);
            }
            return;
        }
        if (targets.isEmpty()) return;

        boolean allSelected = present.isEmpty() || present.equals(targets);
        if (allSelected) {
            writes.put(groupKey, text);
            boolean split = ObjektDescriptionKeys.memberSpecific(season, number);
            if (ObjektDescriptionKeys.jsonException(season, number) || split) {
                // 예외·기존 분리 번호는 멤버 행을 최신 공통 문구로 맞춰 둔다.
                for (String token : present) {
                    writes.put(ObjektDescriptionKeys.memberKey(season, number, token), text);
                }
            }
            if (!ObjektDescriptionKeys.jsonException(season, number)) {
                ObjektDescriptionKeys.clearMemberSpecific(season, number);
                forget.add(scopeKey);
            }
            return;
        }

        boolean alreadySplit = ObjektDescriptionKeys.memberSpecific(season, number);
        String previous = previousText(season, number, cards, existing);
        ObjektDescriptionKeys.markMemberSpecific(season, number);
        remember.add(scopeKey);
        // 공통→분리로 처음 갈 때만 비선택 멤버에 이전 공통 문구를 채운다.
        // 이미 분리된 번호에서는 의도적 빈 설명을 덮지 않는다.
        if (!alreadySplit) {
            for (String token : present) {
                if (targets.contains(token)) continue;
                String key = ObjektDescriptionKeys.memberKey(season, number, token);
                if (!hasText(key, existing, writes) && !previous.isBlank()) {
                    writes.put(key, previous);
                }
            }
        }
        for (String token : targets) {
            writes.put(ObjektDescriptionKeys.memberKey(season, number, token), text);
        }
    }

    private List<Map<String, Object>> shareMembers(Map<String, Object> item, SharePref pref) {
        String season = ObjektDescriptionKeys.season(item);
        String number = ObjektDescriptionKeys.collectionNo(item);
        String currentMember = ObjektDescriptionKeys.memberOf(item);
        boolean memberMode = ObjektDescriptionKeys.memberSpecific(season, number);
        List<Map<String, Object>> siblings = siblings(season, number);
        Set<String> prefMembers = pref.members();
        boolean usePref = !prefMembers.isEmpty();
        String currentText = !usePref && memberMode ? read(item) : "";
        Map<String, String> texts = Map.of();
        if (!usePref && memberMode && !currentText.isBlank() && siblings.size() > 1) {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (Map<String, Object> sibling : siblings) {
                keys.addAll(ObjektDescriptionKeys.readKeys(sibling, ""));
            }
            texts = textsByKey(keys);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> sibling : siblings) {
            String token = ObjektDescriptionKeys.memberOf(sibling);
            if (token.isEmpty()) continue;
            boolean selected;
            if (token.equals(currentMember)) selected = true;
            else if (usePref) selected = prefMembers.contains(token);
            else if (!memberMode) selected = true;
            else if (currentText.isBlank()) selected = false;
            else selected = currentText.equals(firstText(sibling, texts));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("token", token);
            row.put("label", displayMember(sibling, token));
            row.put("selected", selected);
            row.put("current", token.equals(currentMember));
            rows.add(row);
        }
        rows.sort(Comparator
                .comparingInt((Map<String, Object> row) -> memberOrder(String.valueOf(row.get("token"))))
                .thenComparing(row -> String.valueOf(row.get("label")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private List<String> selectedMembers(List<Map<String, Object>> rows, String currentMember, SharePref pref) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (!currentMember.isEmpty()) selected.add(currentMember);
        if (!pref.members().isEmpty()) {
            for (Map<String, Object> row : rows) {
                String token = String.valueOf(row.get("token"));
                if (pref.members().contains(token)) selected.add(token);
            }
        } else {
            for (Map<String, Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("selected"))) selected.add(String.valueOf(row.get("token")));
            }
        }
        return List.copyOf(selected);
    }

    private List<String> selectedNumbers(List<String> seasonNumbers, String currentNumber, SharePref pref) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (currentNumber != null && !currentNumber.isBlank()) selected.add(currentNumber);
        if (!pref.numbers().isEmpty()) {
            Map<String, String> canonical = new HashMap<>();
            for (String number : seasonNumbers) canonical.put(normalize(number), number);
            if (currentNumber != null && !currentNumber.isBlank()) {
                canonical.putIfAbsent(normalize(currentNumber), currentNumber);
            }
            for (String number : pref.numbers()) {
                String value = canonical.get(normalize(number));
                if (value != null) selected.add(value);
            }
        }
        return List.copyOf(selected);
    }

    private List<String> seasonNumbers(String season, String currentNumber) {
        List<String> numbers = objekts.seasonCollectionNumbers(season);
        if (currentNumber == null || currentNumber.isBlank()) return numbers;
        if (numbers.stream().anyMatch(value -> normalize(value).equals(normalize(currentNumber)))) {
            return numbers;
        }
        List<String> withCurrent = new ArrayList<>(numbers.size() + 1);
        withCurrent.add(currentNumber);
        withCurrent.addAll(numbers);
        withCurrent.sort(String.CASE_INSENSITIVE_ORDER);
        return withCurrent;
    }

    private Set<String> resolveNumbers(String season, String currentNumber, Collection<String> numbers) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (currentNumber != null && !currentNumber.isBlank()) out.add(currentNumber.trim());
        if (numbers == null || numbers.isEmpty()) return out;
        List<String> seasonList = objekts.seasonCollectionNumbers(season);
        Set<String> allowed = new LinkedHashSet<>();
        Map<String, String> canonical = new HashMap<>();
        for (String number : seasonList) {
            String key = normalize(number);
            allowed.add(key);
            canonical.putIfAbsent(key, number);
        }
        allowed.add(normalize(currentNumber));
        if (currentNumber != null && !currentNumber.isBlank()) {
            canonical.putIfAbsent(normalize(currentNumber), currentNumber.trim());
        }
        for (String raw : numbers) {
            if (raw == null || raw.isBlank()) continue;
            for (String part : raw.split(",")) {
                String number = part.trim();
                if (number.isEmpty()) continue;
                String key = normalize(number);
                if (!allowed.contains(key)) continue;
                out.add(canonical.getOrDefault(key, number));
            }
        }
        return out;
    }

    private Set<String> resolveMembers(Map<String, Object> item, String currentMember,
                                       Collection<String> members, String requestedScope) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String scope = normalize(requestedScope);
        String season = ObjektDescriptionKeys.season(item);
        String number = ObjektDescriptionKeys.collectionNo(item);
        boolean memberMode = ObjektDescriptionKeys.memberSpecific(season, number);
        if (members != null && !members.isEmpty()) {
            for (String raw : members) {
                if (raw == null || raw.isBlank()) continue;
                for (String part : raw.split(",")) {
                    String token = memberToken(part);
                    if (!token.isEmpty()) out.add(token);
                }
            }
        } else if ("member".equals(scope) || (scope.isEmpty() && memberMode)) {
            if (!currentMember.isEmpty()) out.add(currentMember);
        } else if ("group".equals(scope) || scope.isEmpty() || "text".equals(scope)
                || "same".equals(scope) || "same_text".equals(scope)) {
            for (Map<String, Object> sibling : siblings(season, number)) {
                String token = ObjektDescriptionKeys.memberOf(sibling);
                if (!token.isEmpty()) out.add(token);
            }
        }
        if (!currentMember.isEmpty()) out.add(currentMember);
        return out;
    }

    private String previousText(String season, String number, List<Map<String, Object>> cards,
                                Map<String, ObjektDescription> existing) {
        if (!cards.isEmpty()) {
            String fromCard = firstText(cards.get(0), textMap(existing));
            if (!fromCard.isBlank()) return fromCard;
        }
        return textOf(ObjektDescriptionKeys.groupKey(season, number), existing);
    }

    private List<Map<String, Object>> siblings(String season, String number) {
        return objekts.groupCards(season, number);
    }

    private void flushWrites(Map<String, String> writes, Map<String, ObjektDescription> existing) {
        if (writes.isEmpty()) return;
        Map<String, ObjektDescription> loaded = existing.isEmpty() ? loadEntities(writes.keySet()) : existing;
        List<ObjektDescription> rows = new ArrayList<>(writes.size());
        for (Map.Entry<String, String> entry : writes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) continue;
            ObjektDescription entity = loaded.get(key);
            if (entity == null) {
                entity = new ObjektDescription();
                entity.setObjektKey(key);
            }
            entity.setDescription(entry.getValue());
            rows.add(entity);
        }
        if (!rows.isEmpty()) descriptions.saveAll(rows);
    }

    private void flushScopes(Set<ObjektDescriptionScope.Key> remember, Set<ObjektDescriptionScope.Key> forget) {
        if (!forget.isEmpty()) {
            List<ObjektDescriptionScope> existing = scopes.findAllById(forget);
            if (!existing.isEmpty()) scopes.deleteAllInBatch(existing);
        }
        if (remember.isEmpty()) return;
        Set<ObjektDescriptionScope.Key> existing = new LinkedHashSet<>();
        for (ObjektDescriptionScope row : scopes.findAllById(remember)) {
            if (row.getId() != null) existing.add(row.getId());
        }
        List<ObjektDescriptionScope> create = new ArrayList<>();
        for (ObjektDescriptionScope.Key id : remember) {
            if (existing.contains(id) || forget.contains(id)) continue;
            ObjektDescriptionScope row = new ObjektDescriptionScope();
            row.setId(id);
            create.add(row);
        }
        if (!create.isEmpty()) scopes.saveAll(create);
    }

    private void flushSharePrefs(String season, Collection<String> numbers,
                                 Collection<String> members, Collection<String> selectedNumbers) {
        if (season == null || season.isBlank() || numbers == null || numbers.isEmpty()) return;
        String membersCsv = String.join(",", members == null ? List.of() : members);
        String numbersCsv = String.join(",", selectedNumbers == null ? List.of() : selectedNumbers);
        List<ObjektDescriptionSharePref.Key> ids = new ArrayList<>();
        for (String number : numbers) {
            if (number == null || number.isBlank()) continue;
            ids.add(sharePrefKey(season, number));
        }
        if (ids.isEmpty()) return;
        Map<ObjektDescriptionSharePref.Key, ObjektDescriptionSharePref> existing = new HashMap<>();
        for (ObjektDescriptionSharePref row : sharePrefs.findAllById(ids)) {
            if (row.getId() != null) existing.put(row.getId(), row);
        }
        List<ObjektDescriptionSharePref> rows = new ArrayList<>(ids.size());
        for (ObjektDescriptionSharePref.Key id : ids) {
            ObjektDescriptionSharePref row = existing.getOrDefault(id, new ObjektDescriptionSharePref());
            if (row.getId() == null) row.setId(id);
            row.setMembers(membersCsv);
            row.setNumbers(numbersCsv);
            rows.add(row);
        }
        sharePrefs.saveAll(rows);
    }

    private Map<String, ObjektDescription> loadEntities(Collection<String> keys) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) unique.add(key);
        }
        if (unique.isEmpty()) return new HashMap<>();
        Map<String, ObjektDescription> out = new HashMap<>();
        for (ObjektDescription row : descriptions.findByObjektKeyIn(unique)) {
            if (row != null && row.getObjektKey() != null) out.put(row.getObjektKey(), row);
        }
        return out;
    }

    private static boolean hasText(String key, Map<String, ObjektDescription> existing, Map<String, String> writes) {
        if (writes.containsKey(key)) {
            String value = writes.get(key);
            return value != null && !value.isBlank();
        }
        return !textOf(key, existing).isBlank();
    }

    private static String textOf(String key, Map<String, ObjektDescription> existing) {
        if (key == null || key.isBlank()) return "";
        ObjektDescription row = existing.get(key);
        if (row == null || row.getDescription() == null) return "";
        return row.getDescription().trim();
    }

    private static Map<String, String> textMap(Map<String, ObjektDescription> existing) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, ObjektDescription> entry : existing.entrySet()) {
            String text = entry.getValue() == null ? null : entry.getValue().getDescription();
            if (text != null && !text.isBlank()) out.put(entry.getKey(), text);
        }
        return out;
    }

    private static ObjektDescriptionScope.Key scopeKey(String season, String number) {
        return new ObjektDescriptionScope.Key(
                season.trim().toLowerCase(Locale.ROOT), number.trim().toLowerCase(Locale.ROOT));
    }

    private String read(Map<String, Object> item) {
        return firstText(item, textsByKey(ObjektDescriptionKeys.readKeys(item, "")));
    }

    private String firstText(Map<String, Object> item, Map<String, String> texts) {
        for (String key : ObjektDescriptionKeys.readKeys(item, "")) {
            String value = texts.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private Map<String, String> textsByKey(Collection<String> keys) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, ObjektDescription> entry : loadEntities(keys).entrySet()) {
            String text = entry.getValue().getDescription();
            if (text != null && !text.isBlank()) out.put(entry.getKey(), text);
        }
        return out;
    }

    private SharePref loadSharePref(String season, String number) {
        if (season == null || season.isBlank() || number == null || number.isBlank()) {
            return SharePref.EMPTY;
        }
        return sharePrefs.findById(sharePrefKey(season, number))
                .map(row -> new SharePref(parseMemberCsv(row.getMembers()), parseNumberCsv(row.getNumbers())))
                .orElse(SharePref.EMPTY);
    }

    private static ObjektDescriptionSharePref.Key sharePrefKey(String season, String number) {
        return new ObjektDescriptionSharePref.Key(
                season.trim().toLowerCase(Locale.ROOT),
                number.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> parseMemberCsv(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String token = memberToken(part);
            if (!token.isEmpty()) out.add(token);
        }
        return out;
    }

    private static Set<String> parseNumberCsv(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String number = part.trim();
            if (!number.isEmpty()) out.add(number);
        }
        return out;
    }

    private record SharePref(Set<String> members, Set<String> numbers) {
        static final SharePref EMPTY = new SharePref(Set.of(), Set.of());
    }

    private static String displayMember(Map<String, Object> item, String token) {
        String named = text(item, "member", "memberName");
        if (!named.isBlank()) return named;
        for (Map.Entry<String, String> entry : TripleSMembers.NAME_EN_BY_KO.entrySet()) {
            if (memberToken(entry.getValue()).equals(token)) return entry.getKey();
        }
        return token;
    }

    private static int memberOrder(String token) {
        List<String> names = TripleSMembers.NAMES_KO;
        for (int i = 0; i < names.size(); i++) {
            String english = TripleSMembers.NAME_EN_BY_KO.get(names.get(i));
            if (memberToken(english).equals(token) || memberToken(names.get(i)).equals(token)) return i;
        }
        return 999;
    }

    private static String memberToken(String value) {
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

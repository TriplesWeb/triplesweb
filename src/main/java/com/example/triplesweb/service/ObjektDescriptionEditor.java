package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 설명 저장. 같은 시즌·번호는 일괄({@code group}) 또는 이 카드만({@code member}) 고른다.
 */
@Service
public class ObjektDescriptionEditor {
    private final ObjektService objekts;
    private final ObjektDescriptionRepository descriptions;
    private final ObjektDescriptionScopeRepository scopes;

    public ObjektDescriptionEditor(ObjektService objekts,
                                   ObjektDescriptionRepository descriptions,
                                   ObjektDescriptionScopeRepository scopes) {
        this.objekts = objekts;
        this.descriptions = descriptions;
        this.scopes = scopes;
    }

    public Map<String, Object> view(Map<String, Object> item) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean canGroup = ObjektDescriptionKeys.canGroup(item);
        String season = ObjektDescriptionKeys.season(item);
        String number = ObjektDescriptionKeys.collectionNo(item);
        boolean member = canGroup && ObjektDescriptionKeys.memberSpecific(season, number);
        List<Map<String, Object>> group = canGroup ? siblings(item) : List.of();
        body.put("descriptionScope", !canGroup ? "single" : member ? "member" : "group");
        body.put("descriptionGroupLabel", canGroup ? (season + " " + number).trim() : "");
        body.put("descriptionGroupCount", canGroup ? Math.max(1, group.size()) : 1);
        return body;
    }

    @Transactional
    public String save(Map<String, Object> item, String description, String requestedScope) {
        String text = description == null ? "" : description.trim();
        String scope = resolveScope(item, requestedScope);
        if ("member".equals(scope)) saveMember(item, text);
        else if ("group".equals(scope)) saveGroup(item, text);
        else write(ObjektDescriptionKeys.storageKey(item), text);
        return text;
    }

    private String resolveScope(Map<String, Object> item, String requestedScope) {
        String requested = requestedScope == null ? "" : requestedScope.trim().toLowerCase(Locale.ROOT);
        if ("group".equals(requested) || "member".equals(requested)) {
            return ObjektDescriptionKeys.canGroup(item) ? requested : "single";
        }
        if (!ObjektDescriptionKeys.canGroup(item)) return "single";
        return ObjektDescriptionKeys.memberSpecific(
                ObjektDescriptionKeys.season(item), ObjektDescriptionKeys.collectionNo(item))
                ? "member" : "group";
    }

    private void saveGroup(Map<String, Object> item, String text) {
        String season = ObjektDescriptionKeys.season(item);
        String number = ObjektDescriptionKeys.collectionNo(item);
        write(ObjektDescriptionKeys.groupKey(season, number), text);
        if (ObjektDescriptionKeys.memberSpecific(season, number)) {
            for (Map<String, Object> sibling : siblings(item)) {
                String key = ObjektDescriptionKeys.storageKey(sibling);
                if (key.startsWith("member-")) write(key, text);
            }
        }
        if (!ObjektDescriptionKeys.jsonException(season, number)) forgetScope(season, number);
    }

    private void saveMember(Map<String, Object> item, String text) {
        String season = ObjektDescriptionKeys.season(item);
        String number = ObjektDescriptionKeys.collectionNo(item);
        if (!ObjektDescriptionKeys.memberSpecific(season, number)) {
            String previous = read(item);
            rememberScope(season, number);
            String currentKey = ObjektDescriptionKeys.storageKey(item);
            for (Map<String, Object> sibling : siblings(item)) {
                String key = ObjektDescriptionKeys.storageKey(sibling);
                if (!key.startsWith("member-") || key.equals(currentKey)) continue;
                if (!hasText(key)) write(key, previous);
            }
        }
        String key = ObjektDescriptionKeys.storageKey(item);
        write(key.startsWith("member-") ? key : ObjektDescriptionKeys.groupKey(season, number), text);
    }

    private List<Map<String, Object>> siblings(Map<String, Object> item) {
        String season = normalize(ObjektDescriptionKeys.season(item));
        String number = normalize(ObjektDescriptionKeys.collectionNo(item));
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> candidate : objekts.findCollections("triples")) {
            if (season.equals(normalize(ObjektDescriptionKeys.season(candidate)))
                    && number.equals(normalize(ObjektDescriptionKeys.collectionNo(candidate)))) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private void rememberScope(String season, String number) {
        ObjektDescriptionKeys.markMemberSpecific(season, number);
        ObjektDescriptionScope.Key id = new ObjektDescriptionScope.Key(
                season.trim().toLowerCase(Locale.ROOT), number.trim().toLowerCase(Locale.ROOT));
        if (scopes.existsById(id)) return;
        ObjektDescriptionScope row = new ObjektDescriptionScope();
        row.setId(id);
        scopes.save(row);
    }

    private void forgetScope(String season, String number) {
        ObjektDescriptionKeys.clearMemberSpecific(season, number);
        ObjektDescriptionScope.Key id = new ObjektDescriptionScope.Key(
                season.trim().toLowerCase(Locale.ROOT), number.trim().toLowerCase(Locale.ROOT));
        if (scopes.existsById(id)) scopes.deleteById(id);
    }

    private String read(Map<String, Object> item) {
        return ObjektDescriptionKeys.readKeys(item, "").stream()
                .map(descriptions::findByObjektKey)
                .flatMap(Optional::stream)
                .map(ObjektDescription::getDescription)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private boolean hasText(String key) {
        if (key == null || key.isBlank()) return false;
        return descriptions.findByObjektKey(key)
                .map(ObjektDescription::getDescription)
                .filter(value -> value != null && !value.isBlank())
                .isPresent();
    }

    private void write(String key, String text) {
        if (key == null || key.isBlank()) return;
        ObjektDescription entity = descriptions.findByObjektKey(key).orElseGet(ObjektDescription::new);
        if (entity.getObjektKey() == null) entity.setObjektKey(key);
        entity.setDescription(text);
        descriptions.save(entity);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjektDescriptionSyncTest {
    private final Map<String, ObjektDescription> stored = new HashMap<>();
    private final Map<ObjektDescriptionScope.Key, ObjektDescriptionScope> scopes = new HashMap<>();

    @AfterEach
    void resetRuntime() {
        ObjektDescriptionKeys.clearRuntime();
    }

    @Test
    void loadScopesRunsWhenSyncIsDisabled() {
        ObjektDescriptionScope row = new ObjektDescriptionScope();
        row.setId(new ObjektDescriptionScope.Key("cream02", "323"));
        ObjektDescriptionScopeRepository scopeRepo = mock(ObjektDescriptionScopeRepository.class);
        when(scopeRepo.findAll()).thenReturn(List.of(row));
        ObjektDescriptionSync sync = new ObjektDescriptionSync(
                null, mock(ObjektDescriptionRepository.class), scopeRepo, null, null, false);

        sync.loadScopes();

        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "323"));
    }

    @Test
    void sameSentenceUsesOneGroupKey() {
        ObjektDescriptionSync sync = sync();
        List<Map<String, Object>> catalog = List.of(card("SeoYeon", "401Z"), card("YuBin", "401Z"));

        assertEquals(1, sync.sync(catalog, slug -> "Tokyo trading event", english -> "도쿄 거래 행사입니다"));
        assertEquals("도쿄 거래 행사입니다", stored.get(ObjektDescriptionKeys.groupKey("Cream02", "401Z")).getDescription());
        assertEquals(1, stored.size());
        assertTrue(scopes.isEmpty());
        assertEquals(ObjektDescriptionKeys.groupKey("Cream02", "401Z"),
                ObjektDescriptionKeys.readKeys(catalog.get(0), "").get(0));
    }

    @Test
    void newNumberWithDifferentSentencesUsesMemberKeys() {
        ObjektDescriptionSync sync = sync();
        Map<String, Object> seo = card("SeoYeon", "406Z");
        Map<String, Object> yu = card("YuBin", "406Z");

        assertEquals(2, sync.sync(List.of(seo, yu), slug -> slug.contains("seoyeon") ? "neptune event" : "zenith event",
                this::korean));
        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "406Z"));
        assertFalse(ObjektDescriptionKeys.readKeys(seo, "").contains(ObjektDescriptionKeys.groupKey("Cream02", "406Z")));
    }

    @Test
    void differentSentencesUseMemberKeysAndHideTheGroupRow() {
        ObjektDescription group = description("공통 설명");
        group.setUpdatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        stored.put(ObjektDescriptionKeys.groupKey("Cream02", "402Z"), group);
        ObjektDescriptionSync sync = sync();
        Map<String, Object> seo = card("SeoYeon", "402Z", Instant.parse("2019-06-01T00:00:00Z"));
        Map<String, Object> yu = card("YuBin", "402Z", Instant.now());

        assertEquals(2, sync.sync(List.of(seo, yu), slug -> slug.contains("seoyeon") ? "neptune event" : "zenith event",
                this::korean));

        assertEquals("넵튠 거래 행사입니다",
                stored.get(ObjektDescriptionKeys.memberKey("cream02", "402z", "seoyeon")).getDescription());
        assertEquals("제니스 거래 행사입니다",
                stored.get(ObjektDescriptionKeys.memberKey("cream02", "402z", "yubin")).getDescription());
        assertEquals("공통 설명", stored.get(ObjektDescriptionKeys.groupKey("Cream02", "402Z")).getDescription());
        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "402Z"));
        assertEquals(1, scopes.size());
        List<String> keys = ObjektDescriptionKeys.readKeys(seo, "");
        assertEquals(ObjektDescriptionKeys.memberKey("cream02", "402z", "seoyeon"), keys.get(0));
        assertFalse(keys.contains(ObjektDescriptionKeys.groupKey("Cream02", "402Z")));
    }

    @Test
    void keepsExistingMemberAndAdminText() {
        stored.put(ObjektDescriptionKeys.memberKey("cream02", "403z", "seoyeon"), description("관리자 멤버 설명"));
        stored.put("cream02-yubin-403z", description("관리자 카드 설명"));
        Map<String, Object> seo = card("SeoYeon", "403Z");
        Map<String, Object> yu = card("YuBin", "403Z");
        Map<String, Object> jiwoo = card("JiWoo", "403Z");
        ObjektDescriptionSync sync = sync();

        sync.sync(List.of(seo, yu, jiwoo), slug -> slug.contains("jiwoo") ? "zenith event" : "neptune event", this::korean);

        assertEquals("관리자 멤버 설명",
                stored.get(ObjektDescriptionKeys.memberKey("cream02", "403z", "seoyeon")).getDescription());
        assertEquals("관리자 카드 설명", stored.get("cream02-yubin-403z").getDescription());
        assertFalse(stored.containsKey(ObjektDescriptionKeys.memberKey("cream02", "403z", "yubin")));
        assertEquals("제니스 거래 행사입니다",
                stored.get(ObjektDescriptionKeys.memberKey("cream02", "403z", "jiwoo")).getDescription());
    }

    @Test
    void emptyApolloResponseSavesNothing() {
        ObjektDescriptionSync sync = sync();

        assertEquals(0, sync.sync(List.of(card("SeoYeon", "404Z"), card("YuBin", "404Z")), slug -> "", this::korean));
        assertTrue(stored.isEmpty());
        assertTrue(scopes.isEmpty());
        assertFalse(ObjektDescriptionKeys.memberSpecific("Cream02", "404Z"));
    }

    @Test
    void matchingNewMemberDoesNotAddARow() {
        ObjektDescription group = description("공통 설명");
        group.setUpdatedAt(LocalDateTime.now().minusDays(1));
        stored.put(ObjektDescriptionKeys.groupKey("Cream02", "405Z"), group);
        Map<String, Object> older = card("SeoYeon", "405Z", Instant.now().minus(7, ChronoUnit.DAYS));
        Map<String, Object> newer = card("YuBin", "405Z", Instant.now());
        ObjektDescriptionSync sync = sync();

        assertEquals(0, sync.sync(List.of(older, newer), slug -> "Tokyo trading event", english -> "도쿄 거래 행사입니다"));
        assertEquals(1, stored.size());
        assertFalse(ObjektDescriptionKeys.memberSpecific("Cream02", "405Z"));
    }

    private ObjektDescriptionSync sync() {
        ObjektDescriptionRepository descriptions = mock(ObjektDescriptionRepository.class);
        ObjektDescriptionScopeRepository scopeRepo = mock(ObjektDescriptionScopeRepository.class);
        when(descriptions.findByObjektKey(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(descriptions.save(any())).thenAnswer(call -> {
            ObjektDescription value = call.getArgument(0);
            stored.put(value.getObjektKey(), value);
            return value;
        });
        when(scopeRepo.existsById(any())).thenAnswer(call -> scopes.containsKey(call.getArgument(0)));
        when(scopeRepo.save(any())).thenAnswer(call -> {
            ObjektDescriptionScope value = call.getArgument(0);
            scopes.put(value.getId(), value);
            return value;
        });
        return new ObjektDescriptionSync(null, descriptions, scopeRepo, null, null, true);
    }

    private String korean(String english) {
        return switch (english) {
            case "neptune event" -> "넵튠 거래 행사입니다";
            case "zenith event" -> "제니스 거래 행사입니다";
            case "Tokyo trading event" -> "도쿄 거래 행사입니다";
            default -> english;
        };
    }

    private static Map<String, Object> card(String member, String number) {
        return card(member, number, Instant.parse("2024-01-01T00:00:00Z"));
    }

    private static Map<String, Object> card(String member, String number, Instant createdAt) {
        String slug = "cream02-" + member.toLowerCase() + "-" + number.toLowerCase();
        return Map.of(
                "season", "Cream02",
                "collectionNo", number,
                "member", member,
                "slug", slug,
                "collectionId", "Cream02 " + member + " " + number,
                "createdAt", createdAt.toString());
    }

    private static ObjektDescription description(String text) {
        ObjektDescription entity = new ObjektDescription();
        entity.setDescription(text);
        return entity;
    }
}

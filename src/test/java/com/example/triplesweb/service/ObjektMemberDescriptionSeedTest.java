package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import com.example.triplesweb.util.ObjektDescriptionKeys.MemberText;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjektMemberDescriptionSeedTest {
    @Test
    void insertsOnlyWhenMemberSlugAndCollectionHaveNoText() {
        ObjektDescriptionRepository repository = mock(ObjektDescriptionRepository.class);
        Map<String, ObjektDescription> stored = new HashMap<>();
        when(repository.findByObjektKey(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(repository.save(any())).thenAnswer(call -> {
            ObjektDescription value = call.getArgument(0);
            stored.put(value.getObjektKey(), value);
            return value;
        });
        ObjektMemberDescriptionSeed seed = new ObjektMemberDescriptionSeed(repository);
        MemberText seo = new MemberText("cream02", "335a", "seoyeon", "cream02-seoyeon-335a",
                "Cream02 SeoYeon 335A", "도쿄의 neptune Objekt 거래 이벤트");
        MemberText yu = new MemberText("cream02", "335a", "yubin", "cream02-yubin-335a",
                "Cream02 YuBin 335A", "도쿄의 zenith Objekt 거래 이벤트");
        MemberText edited = new MemberText("cream02", "335a", "jiwoo", "cream02-jiwoo-335a",
                "Cream02 JiWoo 335A", "새 문장");
        stored.put(ObjektDescriptionKeys.memberKey("cream02", "335a", "jiwoo"), description("관리자 설명"));
        stored.put("cream02-sohyun-335a", description("카드 설명"));
        MemberText sohyun = new MemberText("cream02", "335a", "sohyun", "cream02-sohyun-335a",
                "Cream02 SoHyun 335A", "도쿄의 moon Objekt 거래 이벤트");

        assertEquals(2, seed.insertMissing(List.of(seo, yu, edited, sohyun)));
        assertEquals("도쿄의 neptune Objekt 거래 이벤트",
                stored.get(ObjektDescriptionKeys.memberKey("cream02", "335a", "seoyeon")).getDescription());
        assertEquals("관리자 설명",
                stored.get(ObjektDescriptionKeys.memberKey("cream02", "335a", "jiwoo")).getDescription());
        assertFalseStored(stored, ObjektDescriptionKeys.memberKey("cream02", "335a", "sohyun"));

        assertEquals(0, seed.insertMissing(List.of(seo)));
        verify(repository, never()).delete(any());
    }

    private static ObjektDescription description(String text) {
        ObjektDescription entity = new ObjektDescription();
        entity.setDescription(text);
        return entity;
    }

    private static void assertFalseStored(Map<String, ObjektDescription> stored, String key) {
        org.junit.jupiter.api.Assertions.assertFalse(stored.containsKey(key));
    }
}

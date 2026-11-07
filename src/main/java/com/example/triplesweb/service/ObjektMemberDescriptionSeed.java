package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import com.example.triplesweb.util.ObjektDescriptionKeys.MemberText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 멤버별 설명이 다른 오브젝트만 적재한다.
 * 멤버 키가 이미 있거나, 슬러그·컬렉션 ID에 문장이 있으면 덮어쓰지 않는다.
 * 공통 {@code group-} 설명은 예외 카드에서 읽지 않으므로 여기서 옮기지 않는다.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class ObjektMemberDescriptionSeed implements CommandLineRunner {
    private final ObjektDescriptionRepository descriptions;

    @Value("${objekt.description-seed.enabled:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.debug("Objekt member description seed skipped");
            return;
        }
        int inserted = insertMissing(ObjektDescriptionKeys.members());
        if (inserted > 0) log.info("멤버별 오브젝트 설명 {}건을 넣었습니다.", inserted);
    }

    int insertMissing(List<MemberText> rows) {
        int inserted = 0;
        for (MemberText row : rows) {
            if (row.description() == null || row.description().isBlank()) continue;
            String key = ObjektDescriptionKeys.memberKey(row.season(), row.collectionNo(), row.member());
            if (key.isBlank() || descriptions.findByObjektKey(key).isPresent()) continue;
            if (hasText(row.slug()) || hasText(row.collectionId())) continue;
            ObjektDescription entity = new ObjektDescription();
            entity.setObjektKey(key);
            entity.setDescription(row.description().trim());
            descriptions.save(entity);
            inserted++;
        }
        return inserted;
    }

    private boolean hasText(String key) {
        if (key == null || key.isBlank()) return false;
        return descriptions.findByObjektKey(key)
                .map(ObjektDescription::getDescription)
                .filter(text -> text != null && !text.isBlank())
                .isPresent();
    }
}

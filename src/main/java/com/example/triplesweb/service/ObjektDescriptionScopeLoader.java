package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * DB에 저장된 멤버별 설명 범위를 기동 시 런타임 키에 올린다.
 */
@Component
public class ObjektDescriptionScopeLoader {
    private final ObjektDescriptionScopeRepository scopes;

    public ObjektDescriptionScopeLoader(ObjektDescriptionScopeRepository scopes) {
        this.scopes = scopes;
    }

    @PostConstruct
    void loadScopes() {
        for (ObjektDescriptionScope row : scopes.findAll()) {
            if (row.getId() == null) continue;
            ObjektDescriptionKeys.markMemberSpecific(row.getId().getSeason(), row.getId().getCollectionNo());
        }
    }
}

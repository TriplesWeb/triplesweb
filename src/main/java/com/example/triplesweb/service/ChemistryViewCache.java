package com.example.triplesweb.service;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.util.ChemistryMatrixHtml;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 케미 표 HTML 조각 캐시. 페이지 TTFB에서 매트릭스 재생성·중복 직렬화를 피한다.
 */
@Component
public class ChemistryViewCache {

    private static final long TTL_MS = 120_000L;

    private final ChemistryService chemistryService;

    private volatile String theadKo;
    private volatile String theadEn;
    private volatile String tbodyKo;
    private volatile String tbodyEn;
    private volatile long expiresAt;

    public ChemistryViewCache(ChemistryService chemistryService) {
        this.chemistryService = chemistryService;
    }

    public String thead(boolean english) {
        ensureFresh();
        return english ? theadEn : theadKo;
    }

    public String tbody(boolean english) {
        ensureFresh();
        return english ? tbodyEn : tbodyKo;
    }

    public void invalidate() {
        theadKo = null;
        theadEn = null;
        tbodyKo = null;
        tbodyEn = null;
        expiresAt = 0L;
    }

    private void ensureFresh() {
        long now = System.currentTimeMillis();
        if (tbodyKo != null && now < expiresAt) {
            return;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (tbodyKo != null && now < expiresAt) {
                return;
            }
            List<ChemistryDto> pairs = chemistryService.findAll();
            theadKo = ChemistryMatrixHtml.thead(false);
            theadEn = ChemistryMatrixHtml.thead(true);
            tbodyKo = ChemistryMatrixHtml.tbody(pairs, false);
            tbodyEn = ChemistryMatrixHtml.tbody(pairs, true);
            expiresAt = System.currentTimeMillis() + TTL_MS;
        }
    }
}

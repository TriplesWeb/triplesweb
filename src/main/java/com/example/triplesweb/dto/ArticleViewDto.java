package com.example.triplesweb.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/** 게시글 상세 페이지 뷰 DTO. 본문·카테고리·KST 날짜·첨부 목록. */
@Getter @Setter
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleViewDto {
    private Long id;
    private String title;
    private Long displayId;
    private String content;
    /** 마켓 피드에 본문 설명을 노출할지. */
    private boolean hasMarketDescription;
    private String userId;
    private String category;
    private String formattedDate;   // ← 템플릿에서 top-level로도 넣어줄 거지만, 보관도 해둠
    private List<ArticleFileDto> files;
    private boolean hasMemberSwap;
    private boolean showTradeSwap;
    private boolean hasHaveLead;
    private String haveLeadName;
    private String haveLeadColor;
    private boolean hasHaveMore;
    private int haveMoreCount;
    private boolean hasWantLead;
    private String wantLeadName;
    private String wantLeadColor;
    private boolean hasWantMore;
    private int wantMoreCount;
    private List<Map<String, Object>> havePreview;
    private List<Map<String, Object>> wantPreview;
    /** 목록 +N 모달용 전체 썸네일 (캡 없음). */
    private List<Map<String, Object>> haveAll;
    private List<Map<String, Object>> wantAll;
    private boolean hasHavePreview;
    private boolean hasWantPreview;
    private boolean hasObjektPreview;
    private boolean hasHavePreviewMore;
    private int havePreviewMoreCount;
    private boolean hasWantPreviewMore;
    private int wantPreviewMoreCount;
}

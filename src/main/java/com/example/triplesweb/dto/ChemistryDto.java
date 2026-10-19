package com.example.triplesweb.dto;

import com.example.triplesweb.entity.ChemistryLabelTone;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChemistryDto {
    private Long id;
    /** 표시용 한글 이름 (매트릭스 키) */
    private String memberA;
    private String memberB;
    /** Artist FK — 프로필 링크용 */
    private Long artistAId;
    private Long artistBId;
    private String label;
    private String description;
    private String imageUrl;
    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();
    private boolean crossFlag;
    /** 표 칸 글자색: NONE / UNDECIDED / RECENT / FAN */
    @Builder.Default
    private ChemistryLabelTone labelTone = ChemistryLabelTone.NONE;
}

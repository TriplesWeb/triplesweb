package com.example.triplesweb.dto;

import com.example.triplesweb.entity.ChemistryLabelTone;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChemistryGroupDto {
    private Long id;
    private String memberKey;
    @Builder.Default
    private List<String> members = new ArrayList<>();
    @Builder.Default
    private List<Long> artistIds = new ArrayList<>();
    private String label;
    private String description;
    private String labelEn;
    private String descriptionEn;
    private String imageUrl;
    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();
    @Builder.Default
    private ChemistryLabelTone labelTone = ChemistryLabelTone.NONE;
}

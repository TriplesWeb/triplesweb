package com.example.triplesweb.dto;

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
    private String memberA;
    private String memberB;
    private String label;
    private String description;
    private String imageUrl;
    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();
    private boolean deleted;
    private boolean crossFlag;
}

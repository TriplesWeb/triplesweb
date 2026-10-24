package com.example.triplesweb.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ArtistAdminDto {
    private Long artistId;
    private String name;
    private String engName;
    private String nationality;
    private String birthDate;
    private String bornPlace;
    private String birthPlace;
    private String realName;
    private String mbti;
    private String debutDate;
    private String color;
    private String profileImage;
    private String signatureImage;
    /** 영상 링크 목록 [{title,url}] */
    private List<Map<String, String>> soloLinks;
    private List<Map<String, String>> broadcastLinks;
    private List<Long> unitIds;
    private List<UnitOption> units;

    @Getter
    @Builder
    public static class UnitOption {
        private Long unitId;
        private String unitName;
    }
}

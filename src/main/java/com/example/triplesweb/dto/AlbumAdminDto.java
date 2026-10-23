package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumAdminDto {
    private Long albumId;
    private String albumName;
    private String albumImage;
    private String releaseDate;
    private String albumInfo;
    private String albumType;
    /** 앨범 규격(자유 입력). 비어 있으면 미지정 */
    private String albumFormat;
    private Long unitId;
    private String unitName;
    private Long sectionId;
    private String sectionName;
    @Builder.Default
    private List<TrackItem> tracks = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackItem {
        private Integer trackNum;
        private String title;
        private String duration;
        private Boolean isTitle;
        private String melonSongId;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnitOption {
        private Long unitId;
        private String unitName;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionOption {
        private Long sectionId;
        private String name;
    }
}

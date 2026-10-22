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
    private Long unitId;
    private String unitName;
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
}

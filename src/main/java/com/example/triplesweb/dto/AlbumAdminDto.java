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
    /** 앨범 뮤직비디오 YouTube ID(11자) 또는 URL. 비어 있으면 미표시 */
    private String musicVideoYoutubeId;
    private Long unitId;
    private String unitName;
    private Long sectionId;
    private String sectionName;
    @Builder.Default
    private List<Long> artistIds = new ArrayList<>();
    @Builder.Default
    private List<ArtistOption> artists = new ArrayList<>();
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
        private String youtubeVideoId;
        /** 가사 원문. [멤버명] 파트 태그 포함 가능 */
        private String lyrics;
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
    public static class ArtistOption {
        private Long artistId;
        private String name;
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

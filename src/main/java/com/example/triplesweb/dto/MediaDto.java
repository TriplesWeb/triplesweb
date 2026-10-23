package com.example.triplesweb.dto;

import com.example.triplesweb.media.MediaTab;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

/** 미디어(유튜브) 관리 폼/API 바인딩. 탭·영상 ID·제목 등 입력·표시. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaDto {
    @NotBlank
    private String tab;

    @NotBlank
    private String youtubeId;

    @NotBlank
    @Size(max = 200)
    private String title;

    private Long views;
    private String viewCountFmt;  // "123,456"
    private String duration;      // "03:59"
    private String description;

    @Builder.Default
    private boolean addFirst = true;

    /** 폼 입력용 멤버 ID들. 미디어 등록 시 선택하지 않아도 된다. */
    @Builder.Default
    private List<Long> artistIds = new ArrayList<>();

    /** 화면 렌더링용 태그 목록. */
    @Builder.Default
    private List<ArtistTagDto> artists = new ArrayList<>();

    public String normalizedTab() { return MediaTab.from(tab).getKey(); }

}

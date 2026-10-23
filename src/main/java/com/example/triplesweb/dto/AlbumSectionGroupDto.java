package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Album;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 앨범 목록 페이지용 섹션 묶음. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumSectionGroupDto {
    private Long sectionId;
    private String name;
    private boolean other;
    /** 탭/패널 DOM id (예: album-section-1, album-section-other) */
    private String tabKey;
    @Builder.Default
    private List<Album> albums = new ArrayList<>();
}

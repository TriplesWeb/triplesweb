package com.example.triplesweb.dto;

import com.example.triplesweb.media.MediaTab;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MediaDto {
    @NotBlank private String tab;
    @NotBlank private String youtubeId;
    /** 저장 시 비어 있으면 유튜브 메타데이터로 채운다. */
    @Size(max = 200) private String title;
    private Long views;
    private String viewCountFmt;
    private String duration;
    private String description;
    private String artistNames;
    @Builder.Default private boolean addFirst = true;
    public String normalizedTab() { return MediaTab.from(tab == null ? "mv" : tab).getKey(); }
}
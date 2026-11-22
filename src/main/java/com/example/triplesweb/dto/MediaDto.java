package com.example.triplesweb.dto;

import com.example.triplesweb.media.MediaTab;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MediaDto {
    private Long id;
    @NotBlank private String tab;
    @NotBlank private String youtubeId;
    /** 저장 시 비어 있으면 유튜브 메타데이터로 채운다. */
    @Size(max = 200) private String title;
    private Long views;
    private String viewCountFmt;
    private String duration;
    private String description;
    private String artistNames;
    /** 유튜브 공개일. yyyy-MM-dd */
    private String publishedOn;
    @Builder.Default private List<Map<String, Object>> memberBadges = new ArrayList<>();
    @Builder.Default private boolean addFirst = true;
    public boolean hasMembers() {
        return memberBadges != null && !memberBadges.isEmpty();
    }

    public String normalizedTab() { return MediaTab.from(tab == null ? "mv" : tab).getKey(); }

    /** 프로필 등 UI 표시용 탭 한글명 */
    public String getTabLabel() {
        return MediaTab.from(tab == null ? "mv" : tab).getKor();
    }

    /** 화면 표시용 공개일. yyyy.MM.dd */
    public String getPublishedLabel() {
        if (publishedOn == null || publishedOn.isBlank()) return "";
        String value = publishedOn.trim();
        if (value.length() >= 10) return value.substring(0, 10).replace('-', '.');
        return value;
    }
}
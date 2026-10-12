package com.example.triplesweb.dto;

import com.example.triplesweb.media.MediaTab;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaDto {
    @NotBlank
    private String tab;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_-]{6,15}$", message="유튜브 ID 형식이 올바르지 않습니다.")
    private String youtubeId;
    @NotBlank
    @Size(max = 200)
    private String title;

    private Long views;
    private String viewCountFmt;  // "123,456"
    private String duration;      // "03:59"
    private String description;

    private boolean addFirst = true;

    public String normalizedTab() { return MediaTab.from(tab).getKey(); }

    public String getViewCountFmt() {
        if (views == null) return "0";
        return String.format("%,d", views);
    }
}

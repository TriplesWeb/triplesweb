package com.example.triplesweb.dto;

import com.example.triplesweb.media.MediaTab;
import jakarta.validation.constraints.NotBlank;
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

    public String normalizedTab() { return MediaTab.from(tab).getKey(); }

}

package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedMeta {
    @Column(nullable = false, length = 300)
    private String originalName;
    @Column(nullable = false, length = 400)
    private String savedName;
    @Column(nullable = false)
    private String url;
    @Column(nullable = false)
    private long size;
    @Column(nullable = false)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    // 파생 판단자 (DB 칼럼 아님)
    @Transient
    public boolean isImage() { return mediaType ==  MediaType.IMAGE; }
    @Transient
    public boolean isVideo() { return mediaType == MediaType.VIDEO; }

    public String getUrl() {
        // url이 비어 있으면 savedName으로 계산해 반환
        if (url == null || url.isBlank()) {
            return (savedName != null && !savedName.isBlank())
                    ? "/uploads/" + savedName
                    : null;
        }
        return url;
    }
}

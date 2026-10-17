package com.example.triplesweb.dto;

import com.example.triplesweb.entity.MediaType;
import lombok.*;

/** 게시글 첨부 파일 뷰 DTO. Mustache 분기(isImage/isVideo) 및 MIME 힌트용. */
@Builder
@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class ArticleFileDto {
    private Long id;
    private MediaType type;
    private String originalName;
    private String savedName;
    private Long articleId;
    private String category;
    private Long size;

    // 머스태치에서 {{fileType}}로 접근 가능
    public String getFileType() {
        if (type == MediaType.VIDEO) return "video/mp4";
        if (type == MediaType.IMAGE) return "image/jpeg";
        return "application/octet-stream";
    }
    // 머스태치에서 {{#isImage}} / {{#isVideo}} 로 분기
    public boolean isImage() { return type == MediaType.IMAGE; }
    public boolean isVideo() { return type == MediaType.VIDEO; }

}

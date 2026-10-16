package com.example.triplesweb.dto;

import lombok.*;

import java.util.List;

@Getter @Setter
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleViewDto {
    private Long id;
    private String title;
    private Long displayId;
    private int likesCount;
    private String content;
    private String userId;
    private String category;
    private String formattedDate;   // ← 템플릿에서 top-level로도 넣어줄 거지만, 보관도 해둠
    private List<ArticleFileDto> files;
}

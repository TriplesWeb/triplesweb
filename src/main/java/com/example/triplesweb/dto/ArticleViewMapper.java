package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.DateTimeFormatService;

/** {@link com.example.triplesweb.entity.Article}와 첨부 DTO를 {@link ArticleViewDto}로 변환. */
public final class ArticleViewMapper {
    private ArticleViewMapper() {}

    public static ArticleViewDto toView(Article a, java.util.List<ArticleFileDto> files, DateTimeFormatService fmt) {
        ArticleViewDto v = new ArticleViewDto();
        v.setId(a.getId());
        v.setTitle(a.getTitle());
        v.setContent(a.getContent());
        v.setUserId(a.getUserId());
        v.setCategory(a.getCategory());
        v.setFormattedDate(fmt.formatKst(a.getFormattedCreatedAt())); // 핵심
        v.setFiles(files);
        return v;
    }
}

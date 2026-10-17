package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.Comment;
import lombok.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 댓글 목록·API용 뷰 DTO. 게시판명·포맷 날짜 등 {@link com.example.triplesweb.entity.Comment} 확장 필드. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CommentDto extends Comment {
    private Long id;
    private String category;
    private Long articleId;
    private String articleName;
    private String userId;
    private String body;
    private String formattedDate;

    public static CommentDto createCommentDto(Comment comment) {
        Article article = comment.getArticle();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.of("Asia/Seoul"));
        String formatted = "";
        if (comment.getFormattedCreatedAt() != null) {
            formatted = formatter.format(comment.getFormattedCreatedAt().toInstant());
        }
        return new CommentDto(
                comment.getId(),
                article.getCategory(),
                article.getId(),
                getArticleName(article.getCategory()),
                comment.getUserId(),
                comment.getBody(),
                formatted
        );
    }
    private static String getArticleName(String category) {
        return switch (category) {
            case "free" -> "자유게시판";
            case "trade" -> "교환게시판";
            case "notice" -> "공지사항";
            default -> "기타";
        };
    }
}

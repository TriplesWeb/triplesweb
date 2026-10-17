package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 커뮤니티 게시글 엔티티. {@link Comment}·{@link ArticleLike}·{@link ArticleFile} 1:N.
 */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor

public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long displayId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private String category;

    @ToString.Exclude
    @OneToMany(mappedBy = "article", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<ArticleLike> likes;

    @OneToMany(mappedBy = "article", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @Column(nullable = false)
    private int likesCount = 0;

    @ToString.Exclude
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ArticleFile> files = new java.util.ArrayList<>();

    @Column(name = "formatted_created_at", updatable = false)
    private LocalDateTime formattedCreatedAt;

    @Transient
    private String formattedDate;
    @Transient
    private String categoryName;

    public void patch(Article article) {
        if (article.title != null && !article.title.isBlank())
            this.title = article.title;
        if (article.content != null && !article.content.isBlank())
            this.content = article.content;
    }
    @PrePersist
    public void prePersist() {
        if (this.formattedCreatedAt == null) {
            this.formattedCreatedAt = LocalDateTime.now();
        }
    }

    public String getFormattedDate() {
        if (formattedCreatedAt != null) {
            return formattedCreatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        return "";
    }
    @Transient
    public String getFormattedCreatedAt() {
        return getFormattedDate();
    }

    public String getCategoryName() {
        if (category == null) return "기타";

        switch (category) {
            case "free": return "자유게시판";
            case "chat": return "잡담게시판";
            case "notice": return "공지사항";
            case "trade": return "교환게시판";
            case "sell": return "거래게시판";
            case "info": return "정보게시판";
            case "popular": return "인기글";
            default: return "기타";
        }
    }
}

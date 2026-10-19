package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "article")
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

    @OneToMany(mappedBy = "article", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    /** 좋아요 수(별도 like 테이블 없이 카운터만 유지) */
    @Column(nullable = false)
    private int likesCount = 0;

    @ToString.Exclude
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArticleFile> files = new ArrayList<>();

    @Column(name = "formatted_created_at", updatable = false)
    private LocalDateTime formattedCreatedAt;

    @Transient
    private String categoryName;

    public void patch(Article article) {
        if (article.title != null && !article.title.isBlank())
            this.title = article.title;
        if (article.content != null && !article.content.isBlank())
            this.content = article.content;
    }

    public void incrementLikes() {
        this.likesCount = this.likesCount + 1;
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

    public String getCategoryName() {
        if (category == null) return "기타";

        return switch (category) {
            case "free" -> "자유게시판";
            case "chat" -> "잡담게시판";
            case "notice" -> "공지사항";
            case "trade" -> "교환게시판";
            case "sell" -> "거래게시판";
            case "info" -> "정보게시판";
            case "popular" -> "인기글";
            default -> "기타";
        };
    }
}

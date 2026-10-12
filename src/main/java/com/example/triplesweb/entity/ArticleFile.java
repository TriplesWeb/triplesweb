package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Builder
@Table(name = "article_file")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private SavedMeta file;

    @Column(nullable = false, length = 20)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "article_id", nullable = true)
    private Article article;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArticleFile)) return false;
        return id != null && id.equals(((ArticleFile) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Transient
    public boolean isImage() {
        return file != null && file.getMediaType() == MediaType.IMAGE;
    }
    @Transient
    public boolean isVideo() {
        return file != null && file.getMediaType() == MediaType.VIDEO;
    }

    @PrePersist
    @PreUpdate
    public void syncCategory() {
        if (this.article != null) {
            this.category = this.article.getCategory();
        }
    }
}

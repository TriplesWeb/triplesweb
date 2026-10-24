package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 게시글 첨부 파일 엔티티({@code article_file}). {@link Article} N:1, {@link SavedMeta} 임베드.
 */
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

    /**
     * Live DB still has legacy camelCase embed columns (fileType, …).
     * {@link SavedMeta} defaults to snake_case for archive; override here.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "originalName", column = @Column(name = "originalName", nullable = false, length = 300)),
            @AttributeOverride(name = "savedName", column = @Column(name = "savedName", nullable = false, length = 400)),
            @AttributeOverride(name = "url", column = @Column(name = "url", nullable = false)),
            @AttributeOverride(name = "size", column = @Column(name = "size", nullable = false)),
            @AttributeOverride(name = "fileType", column = @Column(name = "fileType", nullable = false)),
            @AttributeOverride(name = "mediaType", column = @Column(name = "mediaType", nullable = false, length = 50))
    })
    private SavedMeta file;

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
}

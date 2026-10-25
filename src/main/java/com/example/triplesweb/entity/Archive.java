package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 아카이브(미디어 자료) 엔티티. {@link SavedMeta} 임베드로 파일 보관.
 */
@Entity
@Table(name = "archive")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Archive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_names", columnDefinition = "TEXT")
    private String artistNames;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Embedded
    private SavedMeta file;

    @Transient
    public MediaType getType() {
        return (file != null && file.getMediaType() != null) ? file.getMediaType() : MediaType.OTHER;
    }
    public Archive(String title, SavedMeta file) {
        this.title = title;
        this.file = file;
    }
}

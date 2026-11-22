package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "artist_names", columnDefinition = "TEXT")
    private String artistNames;

    /** 수동 선택한 소속 유닛. 출연진이 유닛 구성과 달라도 배지에 쓴다. */
    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "registered_on")
    private LocalDate registeredOn;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ArchiveSource source;

    /** 이미지 URL JSON 배열. 표지(첫 장)는 {@link #file}과 동기화. */
    @Column(name = "images_json", columnDefinition = "TEXT")
    private String imagesJson;

    /** 사진 순서와 맞춘 출연 멤버 JSON 배열. 각 항목은 쉼표로 구분한 이름. */
    @Column(name = "image_artists_json", columnDefinition = "TEXT")
    private String imageArtistsJson;

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

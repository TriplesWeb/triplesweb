package com.example.triplesweb.entity;

import com.example.triplesweb.util.ArchiveFolderClassifier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

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
    @ElementCollection
    @CollectionTable(name = "archive_unit", joinColumns = @JoinColumn(name = "archive_id"))
    @Column(name = "unit_id", nullable = false)
    @ToString.Exclude
    private Set<Long> unitIds = new LinkedHashSet<>();

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "registered_on")
    private LocalDate registeredOn;

    /** {@link ArchiveSource#getCode()} 와 같은 키. */
    @Column(length = 32)
    private String source;

    /** 이미지 URL JSON 배열. 표지(첫 장)는 {@link #file}과 동기화. */
    @Column(name = "images_json", columnDefinition = "TEXT")
    private String imagesJson;

    /** 사진 순서와 맞춘 출연 멤버 JSON 배열. 각 항목은 쉼표로 구분한 이름. */
    @Column(name = "image_artists_json", columnDefinition = "TEXT")
    private String imageArtistsJson;

    @Column(name = "cast_member_mask", nullable = false)
    @Setter(AccessLevel.NONE)
    private int castMemberMask;

    @Column(name = "cast_member_count", nullable = false)
    @Setter(AccessLevel.NONE)
    private int castMemberCount;

    @Column(name = "cast_member_rank", nullable = false)
    @Setter(AccessLevel.NONE)
    private int castMemberRank = 24;

    @PrePersist
    @PreUpdate
    private void updateCastIndex() {
        castMemberMask = ArchiveFolderClassifier.memberMask(artistNames);
        castMemberCount = Integer.bitCount(castMemberMask);
        castMemberRank = ArchiveFolderClassifier.memberRank(castMemberMask);
    }

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

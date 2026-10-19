package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 유튜브 미디어 카탈로그. JSON 파일 대신 DB가 단일 소스.
 */
@Entity
@Table(
        name = "media",
        uniqueConstraints = @UniqueConstraint(name = "uk_media_youtube", columnNames = {"youtube_id"}),
        indexes = @Index(name = "idx_media_tab_sort", columnList = "tab, sort_order")
)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String youtubeId;

    @Column(nullable = false, length = 32)
    private String tab;

    @Column(nullable = false, length = 255)
    private String mediaTitle;

    @Column(nullable = false, length = 32)
    private String mediaDur;

    @Column(nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 탭 내 정렬(작을수록 앞). addFirst 시 더 작은 값을 부여. */
    @Column(nullable = false)
    @Builder.Default
    private Long sortOrder = 0L;
}

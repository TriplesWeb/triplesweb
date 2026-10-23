package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 유튜브 미디어 카탈로그. 멤버 연결은 main의 media_artist 관계 테이블을 사용한다.
 */
@Entity
@Table(
        name = "media",
        uniqueConstraints = @UniqueConstraint(name = "uk_media_youtube", columnNames = {"youtube_id"}),
        indexes = @Index(name = "idx_media_tab_sort", columnList = "tab, sort_order")
)
@Getter
@Setter
@ToString(exclude = "artists")
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

    @Column(nullable = false)
    @Builder.Default
    private Long sortOrder = 0L;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "media_artist",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    @Builder.Default
    private Set<Artist> artists = new LinkedHashSet<>();

    @Transient
    public String getArtistNames() {
        if (artists == null || artists.isEmpty()) return "";
        return artists.stream().map(Artist::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(","));
    }
}
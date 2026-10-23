package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media", uniqueConstraints = @UniqueConstraint(name = "uk_media_youtube", columnNames = {"youtube_id"}),
        indexes = @Index(name = "idx_media_tab_sort", columnList = "tab, sort_order"))
@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor @Builder
public class Media {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32) private String youtubeId;
    @Column(nullable = false, length = 32) private String tab;
    @Column(nullable = false, length = 255) private String mediaTitle;
    @Column(nullable = false, length = 32) private String mediaDur;
    @Column(nullable = false) @Builder.Default private Long viewCount = 0L;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "artist_names", columnDefinition = "TEXT") private String artistNames;
    @Column(nullable = false) @Builder.Default private Long sortOrder = 0L;
}
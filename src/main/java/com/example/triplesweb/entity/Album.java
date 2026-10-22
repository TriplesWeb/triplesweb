package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 앨범 엔티티. {@link Unit} N:1, {@link Track} 1:N.
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@ToString(exclude = {"unit","tracks"})
@NoArgsConstructor
@AllArgsConstructor
public class Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long albumId;

    @Column(unique = true, nullable = false)
    private String albumName;

    private String albumImage;

    @Column(nullable = false)
    private String releaseDate;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String albumInfo;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Track> tracks = new ArrayList<>();


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "album_type", nullable = false, length = 16)
    private AlbumType albumType = AlbumType.SUBUNIT;

    /** Mustache용: 10인 완전체 배지 */
    public boolean isFull10() {
        return albumType == AlbumType.FULL_10;
    }

    /** Mustache용: 24인 완전체 배지 */
    public boolean isFull24() {
        return albumType == AlbumType.FULL_24;
    }
}

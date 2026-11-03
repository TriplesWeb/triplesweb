package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 앨범 엔티티. {@link Unit} N:1, {@link Track} 1:N, {@link Artist} N:M(솔로·멀티).
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@ToString(exclude = {"unit", "tracks", "section", "artists"})
@NoArgsConstructor
public class Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long albumId;

    @Column(unique = true, nullable = false)
    private String albumName;

    private String albumImage;

    @Transient
    public String getDisplayAlbumImage() {
        return com.example.triplesweb.util.ImageUrls.orPlaceholder(albumImage);
    }

    @Column(nullable = false)
    private String releaseDate;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String albumInfo;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Track> tracks = new ArrayList<>();


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    /** 솔로·멀티 멤버 참여. 참여 유닛과 상호 배타. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "album_artist",
            joinColumns = @JoinColumn(name = "album_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> artists = new LinkedHashSet<>();

    /** 목록 구분(섹션). null이면 「기타」 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private AlbumSection section;

    @Enumerated(EnumType.STRING)
    @Column(name = "album_type", nullable = false, length = 16)
    private AlbumType albumType = AlbumType.SUBUNIT;

    /** 앨범 규격(자유 입력). 비어 있으면 미지정 */
    @Column(name = "album_format", length = 120)
    private String albumFormat;

    public Album(
            Long albumId,
            String albumName,
            String albumImage,
            String releaseDate,
            String albumInfo,
            List<Track> tracks,
            Unit unit,
            AlbumType albumType
    ) {
        this.albumId = albumId;
        this.albumName = albumName;
        this.albumImage = albumImage;
        this.releaseDate = releaseDate;
        this.albumInfo = albumInfo;
        this.tracks = tracks != null ? tracks : new ArrayList<>();
        this.unit = unit;
        this.albumType = albumType != null ? albumType : AlbumType.SUBUNIT;
    }

    /** Mustache용: 10인 완전체 배지 */
    public boolean isFull10() {
        return albumType == AlbumType.FULL_10;
    }

    /** Mustache용: 24인 완전체 배지 */
    public boolean isFull24() {
        return albumType == AlbumType.FULL_24;
    }

    /** Mustache용: 기타(솔로·멀티 멤버 등) */
    public boolean isOther() {
        return albumType == AlbumType.OTHER;
    }

    /** Mustache용: 규격이 입력된 경우 */
    public boolean isHasAlbumFormat() {
        return albumFormat != null && !albumFormat.isBlank();
    }

    /** Mustache용: 규격 표시 문구 */
    public String getAlbumFormatLabel() {
        return isHasAlbumFormat() ? albumFormat.trim() : null;
    }

    /** Mustache용: 소개 문구가 있을 때 */
    public boolean isHasAlbumInfo() {
        return albumInfo != null && !albumInfo.isBlank();
    }

    /** Mustache용: 참여 멤버가 있을 때 */
    public boolean isHasArtists() {
        return artists != null && !artists.isEmpty();
    }

    /** Mustache용: artistId 순 정렬된 참여 멤버 */
    public List<Artist> getOrderedArtists() {
        if (artists == null || artists.isEmpty()) return List.of();
        return artists.stream()
                .sorted(Comparator.comparing(a -> a.getArtistId() != null ? a.getArtistId() : 0L))
                .collect(Collectors.toList());
    }
}

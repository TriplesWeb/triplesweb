package com.example.triplesweb.entity;

import com.example.triplesweb.util.TrackInfo;
import jakarta.persistence.*;
import lombok.*;

/**
 * 앨범 수록곡 엔티티. {@link Album} N:1.
 */
@Entity
@Table(name = "track")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"album"})
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trackId;

    @Column(nullable = false)
    private int trackNum;

    @Column(nullable = false)
    private String title; // 노래 제목

    private String duration; // 곡 길이 (예: "3:12")

    private Boolean isTitle;

    /** Melon song ID for a direct track page link (optional). */
    @Column(name = "melon_song_id", length = 40)
    private String melonSongId;

    /** Official YouTube video ID (11 chars) for a direct watch link (optional). */
    @Column(name = "youtube_video_id", length = 20)
    private String youtubeVideoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    /** Compatibility constructor for existing initializers. */
    public Track(Long trackId, int trackNum, String title, String duration, Boolean isTitle, Album album) {
        this.trackId = trackId;
        this.trackNum = trackNum;
        this.title = title;
        this.duration = duration;
        this.isTitle = isTitle;
        this.album = album;
        this.melonSongId = null;
        this.youtubeVideoId = null;
    }
}

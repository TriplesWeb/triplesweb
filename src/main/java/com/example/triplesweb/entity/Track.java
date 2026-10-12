package com.example.triplesweb.entity;

import com.example.triplesweb.util.TrackInfo;
import jakarta.persistence.*;
import lombok.*;

@Entity
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;
    }

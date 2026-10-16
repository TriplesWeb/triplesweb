package com.example.triplesweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Media {
    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String tab;

    @Column(nullable = false)
    private String mediaTitle;

    @Column(nullable = false)
    private String mediaDur;

    @Column(nullable = false)
    private String youtubeId;

    @Column(nullable = false)
    private String viewCount;

    private String description;
}

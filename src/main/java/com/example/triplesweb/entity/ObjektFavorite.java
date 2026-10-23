package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "objekt_favorite", uniqueConstraints = @UniqueConstraint(
        name = "uk_objekt_favorite_visitor_objekt", columnNames = {"visitor_key", "objekt_key"}))
@Getter @Setter @NoArgsConstructor
public class ObjektFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_key", nullable = false, length = 100)
    private String visitorKey;

    @Column(name = "objekt_key", nullable = false, length = 255)
    private String objektKey;

    @Column(name = "collection_id", length = 255)
    private String collectionId;

    @Column(name = "member_name", length = 100)
    private String memberName;

    @Column(name = "thumbnail_image", length = 1000)
    private String thumbnailImage;

    @Column(name = "front_image", length = 1000)
    private String frontImage;

    @Column(name = "back_image", length = 1000)
    private String backImage;

    @Column(name = "season_name", length = 100)
    private String season;

    @Column(name = "class_name", length = 100)
    private String objektClass;

    @Column(name = "collection_no", length = 100)
    private String collectionNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

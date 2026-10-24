package com.example.triplesweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * objekt.top 컬렉션 스냅샷. 페이지는 이 테이블을 읽고, 갱신 주기에 API 결과로 다시 채운다.
 */
@Entity
@Table(name = "objekt_catalog", uniqueConstraints = @UniqueConstraint(name = "uk_objekt_catalog_collection", columnNames = "collection_id"))
@Getter
@Setter
@NoArgsConstructor
public class ObjektCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", length = 80)
    private String externalId;

    @Column(name = "created_at", length = 40)
    private String createdAt;

    @Column(length = 200)
    private String slug;

    @Column(name = "collection_id", nullable = false, length = 120)
    private String collectionId;

    @Column(length = 80)
    private String season;

    @Column(length = 200)
    private String member;

    @Column(length = 80)
    private String artist;

    @Column(name = "collection_no", length = 40)
    private String collectionNo;

    @Column(name = "objekt_class", length = 80)
    private String objektClass;

    @Column(name = "thumbnail_image", length = 500)
    private String thumbnailImage;

    @Column(name = "front_image", length = 500)
    private String frontImage;

    @Column(name = "back_image", length = 500)
    private String backImage;

    @Column(name = "background_color", length = 40)
    private String backgroundColor;

    @Column(name = "text_color", length = 40)
    private String textColor;

    @Column(name = "on_offline", length = 40)
    private String onOffline;

    @Column(name = "has_audio", length = 20)
    private String hasAudio;

    @Column(name = "original_front_image", length = 500)
    private String originalFrontImage;

    @Column(name = "original_back_image", length = 500)
    private String originalBackImage;

    @Column(name = "front_media", length = 500)
    private String frontMedia;

    @Column(name = "band_image_url", length = 500)
    private String bandImageUrl;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "season_key", length = 80)
    private String seasonKey;

    @Column(name = "class_key", length = 80)
    private String classKey;

    @Column(name = "number_value", nullable = false)
    private int numberValue;
}

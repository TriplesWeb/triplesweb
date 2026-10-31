package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "article_objekt", uniqueConstraints = @UniqueConstraint(name = "uk_article_objekt", columnNames = {"article_id", "objekt_key"}))
@Getter @Setter @NoArgsConstructor
public class ArticleObjekt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, foreignKey = @ForeignKey(name = "fk_article_objekt_article"))
    private Article article;
    @Column(name = "objekt_key", nullable = false, length = 255) private String objektKey;
    /** HAVE = offering, WANT = looking for. Non-trade boards store HAVE only. */
    @Column(name = "side", nullable = false, length = 10) private String side = "HAVE";
    @Column(name = "collection_id", length = 255) private String collectionId;
    @Column(name = "member_name", length = 100) private String memberName;
    @Column(name = "thumbnail_image", length = 1000) private String thumbnailImage;
    @Column(name = "front_image", length = 1000) private String frontImage;
    @Column(name = "back_image", length = 1000) private String backImage;
    @Column(name = "season_name", length = 100) private String season;
    @Column(name = "class_name", length = 100) private String objektClass;
    @Column(name = "collection_no", length = 100) private String collectionNo;
}
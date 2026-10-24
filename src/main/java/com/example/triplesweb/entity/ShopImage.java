package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 상품 이미지 엔티티. {@link Shop} N:1, 파일 메타는 {@link SavedMeta} 임베드.
 */
@Entity
@Table(name = "shop_image")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Shop shop;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "originalName", column = @Column(name = "originalName", nullable = false, length = 300)),
            @AttributeOverride(name = "savedName", column = @Column(name = "savedName", nullable = false, length = 400)),
            @AttributeOverride(name = "url", column = @Column(name = "url", nullable = false)),
            @AttributeOverride(name = "size", column = @Column(name = "size", nullable = false)),
            @AttributeOverride(name = "fileType", column = @Column(name = "fileType", nullable = false)),
            @AttributeOverride(name = "mediaType", column = @Column(name = "mediaType", nullable = false, length = 50))
    })
    private SavedMeta file;
}

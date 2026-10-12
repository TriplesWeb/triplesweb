package com.example.triplesweb.entity;

import com.example.triplesweb.util.ShopCategory;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String name;

    @Column(nullable=false)
    private int price;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ShopImage> images = new ArrayList<>();

    private boolean soldout;

    @Column(nullable=false)
    private String category;

    public String normalizedTab() { return ShopCategory.from(category).getKey(); }

    public void addImage(ShopImage img) {
        images.add(img);
        img.setShop(this);
    }
}

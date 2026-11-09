package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 굿즈리스트 목록 구분(섹션). 관리자가 이름을 만들고 샵 카드를 배정한다.
 */
@Entity
@Table(name = "shop_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ShopSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false)
    private int sortOrder;
}

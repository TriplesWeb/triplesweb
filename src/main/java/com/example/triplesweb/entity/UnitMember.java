package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 아티스트와 유닛의 소속 관계를 나타내는 조인 엔티티.
 * {@link Artist}·{@link Unit} 각각 N:1.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"artist", "unit"})
public class UnitMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_Id")
    private Artist artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;
}

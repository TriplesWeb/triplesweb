package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 아티스트(멤버) 프로필 엔티티. {@link UnitMember}로 유닛 소속 1:N.
 */
@Entity
@Getter
@Setter
@ToString (exclude = {"affiliatedUnit"})
@NoArgsConstructor
@AllArgsConstructor


public class Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long artistId;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String engName;

    @Column(nullable = false)
    private String nationality;

    @Column(nullable = false)
    private String birthDate;

    @Column(nullable = false)
    private String birthPlace;

    @Column(nullable = false)
    private String debutDate;

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnitMember> affiliatedUnit = new ArrayList<>();

    private String profileImage;

    @Column(nullable = false)
    private String color;
}

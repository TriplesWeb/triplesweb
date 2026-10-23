package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 앨범 목록 구분(섹션). 관리자가 이름을 만들고 앨범을 배정한다.
 */
@Entity
@Table(name = "album_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AlbumSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false)
    private int sortOrder;
}

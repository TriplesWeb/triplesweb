package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 멤버 페어 케미. {@link Artist} 두 명에 FK로 연결한다.
 * artistA/artistB는 표시 순서 기준 오름차순으로 정규화한다.
 */
@Entity
@Table(
        name = "chemistry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chemistry_pair",
                columnNames = {"artist_a_id", "artist_b_id"}
        )
)
@Getter
@Setter
@ToString(exclude = {"artistA", "artistB"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chemistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 마이그레이션 중 null 허용 — SchemaImproveRunner가 NOT NULL·FK를 맞춘다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "artist_a_id")
    private Artist artistA;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "artist_b_id")
    private Artist artistB;

    /** 표 셀에 표시되는 짧은 케미명 (줄바꿈은 \\n) */
    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(length = 512)
    private String imageUrl;

    /** 하이퍼링크 JSON 객체: {"표시텍스트":"https://..."} */
    @Column(columnDefinition = "TEXT")
    private String linksJson;

    @Column(nullable = false)
    @Builder.Default
    private boolean crossFlag = false;

    /** 표 칸 케미명 글자색: 미선택 / 미정 / 최근 / WAV */
    @Enumerated(EnumType.STRING)
    @Column(name = "label_tone", nullable = false, length = 20)
    @Builder.Default
    private ChemistryLabelTone labelTone = ChemistryLabelTone.NONE;
}

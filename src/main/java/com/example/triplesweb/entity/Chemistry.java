package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 멤버 페어 케미(이름·설명·이미지). 정적 JSON/JS 위에 덮어쓰는 DB 오버레이.
 * memberA/memberB는 멤버 인덱스 기준 오름차순으로 정규화한다.
 */
@Entity
@Table(
        name = "Chemistry",
        uniqueConstraints = @UniqueConstraint(name = "uk_chemistry_pair", columnNames = {"memberA", "memberB"})
)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chemistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 멤버 한글 이름 (인덱스 작은 쪽) */
    @Column(nullable = false, length = 40)
    private String memberA;

    /** 멤버 한글 이름 (인덱스 큰 쪽) */
    @Column(nullable = false, length = 40)
    private String memberB;

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

    /** true면 표에서 해당 페어 칸을 비움(정적 데이터 포함 숨김) */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean crossFlag = false;
}

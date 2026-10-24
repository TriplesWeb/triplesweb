package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 3인 이상 멤버 집합 케미.
 * {@code memberKey}는 정렬된 artistId를 쉼표로 이은 값으로 유일하다.
 */
@Entity
@Table(
        name = "chemistry_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chemistry_group_member_key",
                columnNames = {"member_key"}
        )
)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChemistryGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 예: "1,5,12" (artistId 오름차순) */
    @Column(name = "member_key", nullable = false, length = 255)
    private String memberKey;

    /** JSON 배열: ["윤서연","이지우","김채연"] (표시 순서) */
    @Column(name = "members_json", nullable = false, columnDefinition = "TEXT")
    private String membersJson;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "label_en", length = 120)
    private String labelEn;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "links_json", columnDefinition = "TEXT")
    private String linksJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "label_tone", nullable = false, length = 20)
    @Builder.Default
    private ChemistryLabelTone labelTone = ChemistryLabelTone.NONE;
}

package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 이벤트 목록 구분(섹션). 관리자가 이름을 만들고 이벤트를 배정한다.
 */
@Entity
@Table(name = "event_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EventSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false)
    private int sortOrder;
}

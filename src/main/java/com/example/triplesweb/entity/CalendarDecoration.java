package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 달력에 스티커처럼 표시하는 유닛/기념일 전용 일정. 일반 Schedule과 분리한다. */
@Entity
@Table(name = "calendar_decoration")
@Getter
@Setter
@NoArgsConstructor
public class CalendarDecoration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 24)
    private String kind;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(length = 80)
    private String unitName;

    @Column(length = 500)
    private String description;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 16)
    private String color = "#00f5d4";
}

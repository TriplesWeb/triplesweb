package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 일정(캘린더) 엔티티. 제목과 시작·종료일만 보관.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(name = "start_date")
    private LocalDate start;

    @Column(name = "end_date")
    private LocalDate end;

    public Schedule(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.start = startDate;
        this.end = endDate;
    }
}

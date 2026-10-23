package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 일정(캘린더) 엔티티. 제목과 시작·종료일만 보관.
 */
@Entity
@Table(name = "schedule")
@Getter
@Setter
@ToString(exclude = {"artists"})
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

    /** 출연/관련 멤버. 선택하지 않으면 단체 일정으로 취급한다. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "schedule_artist",
            joinColumns = @JoinColumn(name = "schedule_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> artists = new LinkedHashSet<>();

    public Schedule(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.start = startDate;
        this.end = endDate;
    }
}

package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "schedule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Schedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String title;
    @Column(name = "start_date") private LocalDate start;
    @Column(name = "end_date") private LocalDate end;
    @Column(name = "artist_names", columnDefinition = "TEXT") private String artistNames;
    public Schedule(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title; this.start = startDate; this.end = endDate;
    }
    public Schedule(String title, LocalDate startDate, LocalDate endDate, String artistNames) {
        this.title = title; this.start = startDate; this.end = endDate; this.artistNames = artistNames;
    }
}
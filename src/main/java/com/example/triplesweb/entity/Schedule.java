package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일정(캘린더) 엔티티. 멤버 연결은 main의 schedule_artist 관계 테이블을 사용한다.
 */
@Entity
@Table(name = "schedule")
@Getter
@Setter
@ToString(exclude = "artists")
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ScheduleCategory category = ScheduleCategory.AUTO;

    @Column(name = "start_date")
    private LocalDate start;

    @Column(name = "end_date")
    private LocalDate end;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "schedule_artist",
            joinColumns = @JoinColumn(name = "schedule_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> artists = new LinkedHashSet<>();

    public Schedule(Long id, String title, LocalDate start, LocalDate end, Set<Artist> artists) {
        this.id=id; this.title=title; this.start=start; this.end=end; this.artists=artists;
    }

    public Schedule(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.start = startDate;
        this.end = endDate;
    }

    @Transient
    public String getArtistNames() {
        if (artists == null || artists.isEmpty()) return "";
        return artists.stream().map(Artist::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(","));
    }
}
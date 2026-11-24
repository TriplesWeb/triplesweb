package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ScheduleRangeRepositoryTest {
    @Autowired ScheduleRepository schedules;
    @Autowired ArtistRepository artists;

    @Test
    void includesBothBoundaryDaysAndKeepsEveryArtistOnAMatchedEvent() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate exclusiveEnd = start.plusMonths(1);
        Artist first = artist("첫째", "First");
        Artist second = artist("둘째", "Second");

        save("끝나는 날 포함", start.minusDays(4), start, first, second);
        save("하루 일정", start.plusDays(1), null, first);
        save("다른 멤버", start, null, second);
        save("지난 일정", start.minusDays(1), null, first);
        save("다음 달 일정", exclusiveEnd, null, first);

        var filtered = schedules.findOverlapping(start, exclusiveEnd, first.getName());
        assertThat(filtered).extracting(Schedule::getTitle).containsExactly("끝나는 날 포함", "하루 일정");
        assertThat(filtered.get(0).getArtists()).extracting(Artist::getName)
                .containsExactlyInAnyOrder(first.getName(), second.getName());
        assertThat(schedules.findOverlapping(start, exclusiveEnd, null)).hasSize(3);
        assertThat(schedules.findUpcomingStartDates(start, PageRequest.of(0, 2)))
                .containsExactly(start.minusDays(4), start);
    }

    private void save(String title, LocalDate start, LocalDate end, Artist... members) {
        Schedule schedule = new Schedule(title, start, end);
        schedule.setArtists(new LinkedHashSet<>(List.of(members)));
        schedules.save(schedule);
    }

    private Artist artist(String name, String english) {
        Artist artist = new Artist();
        artist.setName(name);
        artist.setEngName(english);
        artist.setBirthDate("2000-01-01");
        artist.setNationality("대한민국");
        artist.setColor("#123456");
        artist.setRevealDate("2020-01-01");
        artist.setDormEntryDate("2020-01-01");
        return artists.save(artist);
    }
}

package com.example.triplesweb.util;

import com.example.triplesweb.entity.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SchedulePresentationTest {
    @Test void legacyTitlesReceiveExpectedCategoriesAndColors() {
        var expected=Map.of("영상통화 팬사인회","#a78bfa","라디오 방송","#fb923c","음원 발매","#f87171","대학교 축제","#7dd3fc");
        expected.forEach((title,color)->{
            Schedule schedule=new Schedule(title,LocalDate.of(2026,10,28),null);
            assertThat(SchedulePresentation.form(schedule,List.of()).getColor()).isEqualTo(color);
            assertThat(schedule.getCategory()).isEqualTo(ScheduleCategory.AUTO);
        });
        Schedule schedule=new Schedule("음원 발매",LocalDate.now(),null);
        schedule.setCategory(ScheduleCategory.EVENT);
        assertThat(SchedulePresentation.form(schedule,List.of()).getColor()).isEqualTo("#7dd3fc");
    }
    @Test void anniversaryFindsOfficialMemberColorAndDoesNotDuplicateCakeOrMutateTitle() {
        Artist artist=new Artist();artist.setArtistId(1L);artist.setName("윤서연");artist.setEngName("SeoYeon");artist.setColor("#22aeff");
        Schedule legacy=new Schedule("윤서연 생일",LocalDate.now(),null);
        assertThat(SchedulePresentation.form(legacy,List.of(artist)).getColor()).isEqualTo("#22aeff");
        legacy.setTitle("🎂 윤서연 생일");
        assertThat(SchedulePresentation.form(legacy,List.of(artist)).getDisplayTitle()).isEqualTo("🎂 윤서연 생일");
        assertThat(legacy.getTitle()).isEqualTo("🎂 윤서연 생일");
        artist.setColor("url(evil)");
        assertThat(SchedulePresentation.form(legacy,List.of(artist)).getColor()).isEqualTo("#00f5d4");
    }
}

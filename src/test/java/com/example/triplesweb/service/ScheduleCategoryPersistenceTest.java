package com.example.triplesweb.service;

import com.example.triplesweb.entity.*;
import com.example.triplesweb.repository.*;
import com.example.triplesweb.util.SchedulePresentation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(ScheduleService.class)
class ScheduleCategoryPersistenceTest {
    @Autowired ScheduleService service;
    @Autowired ScheduleRepository schedules;
    @Autowired ArtistRepository artists;
    @Autowired EntityManager entityManager;

    @Test void categoryAndMemberColorSurviveReloadAndLegacyEditKeepsCategory() {
        Artist artist=new Artist();artist.setName("윤서연");artist.setEngName("seoyeon");
        artist.setNationality("대한민국");artist.setBirthDate("2003-08-06");artist.setDebutDate("2022-05-01");artist.setColor("#22aeff");
        artists.saveAndFlush(artist);
        LocalDate date=LocalDate.of(2026,10,28);
        Schedule saved=service.save("윤서연 생일",date,date,"윤서연",ScheduleCategory.ANNIVERSARY);
        Long id=saved.getId();schedules.flush();entityManager.clear();
        Schedule loaded=schedules.findById(id).orElseThrow();
        assertThat(loaded.getCategory()).isEqualTo(ScheduleCategory.ANNIVERSARY);
        var view=SchedulePresentation.form(loaded,List.of(artist));
        assertThat(view.getColor()).isEqualTo("#22aeff");
        assertThat(view.getDisplayTitle()).isEqualTo("🎂 윤서연 생일");
        assertThat(view.getTitle()).isEqualTo("윤서연 생일");
        assertThat(view.getEnd()).isEqualTo(date.plusDays(1));
        service.update(id.toString(),"수정한 기념일",date,date,"윤서연");
        schedules.flush();entityManager.clear();
        assertThat(schedules.findById(id).orElseThrow().getCategory()).isEqualTo(ScheduleCategory.ANNIVERSARY);
        service.update(id.toString(),"방송",date,date,"윤서연",ScheduleCategory.BROADCAST);
        schedules.flush();entityManager.clear();
        assertThat(SchedulePresentation.form(schedules.findById(id).orElseThrow(),List.of(artist)).getColor()).isEqualTo("#fb923c");
    }
    @Test void invalidDateRangeDoesNotSave() {
        LocalDate day=LocalDate.of(2026,10,28);
        assertThatThrownBy(()->service.save("발매",day,day.minusDays(1),"",ScheduleCategory.RELEASE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("종료 날짜");
        assertThat(schedules.count()).isZero();
    }
}

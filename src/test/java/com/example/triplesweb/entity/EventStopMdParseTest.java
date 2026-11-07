package com.example.triplesweb.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventStopMdParseTest {

    @Test
    void parsesCategoriesAndPrices() {
        EventStop stop = new EventStop();
        stop.setMdList(String.join("\n",
                "# Official MD",
                "!/uploads/images/md-official.jpg",
                "응원봉|45000",
                "슬로건|15000",
                "# MD Benefit",
                "img:https://example.com/benefit.png",
                "특전 포토카드"));

        List<EventStop.MdCategory> cats = stop.getMdCategories();
        assertThat(cats).hasSize(2);
        assertThat(cats.get(0).getTitle()).isEqualTo("Official MD");
        assertThat(cats.get(0).getKey()).contains("official");
        assertThat(cats.get(0).isHasNoticeImages()).isTrue();
        assertThat(cats.get(0).getNoticeImages()).containsExactly("/uploads/images/md-official.jpg");
        assertThat(cats.get(0).getItems()).hasSize(2);
        assertThat(cats.get(0).getItems().get(0).getName()).isEqualTo("응원봉");
        assertThat(cats.get(0).getItems().get(0).getPrice()).isEqualTo("45000");
        assertThat(cats.get(1).getTitle()).isEqualTo("MD Benefit");
        assertThat(cats.get(1).getNoticeImages()).containsExactly("https://example.com/benefit.png");
        assertThat(cats.get(1).getItems()).hasSize(1);
        assertThat(cats.get(1).getItems().get(0).isHasPrice()).isFalse();
    }

    @Test
    void imageOnlyCategoryIsKept() {
        EventStop stop = new EventStop();
        stop.setMdList("# Official MD\n!/images/notice.png");

        List<EventStop.MdCategory> cats = stop.getMdCategories();
        assertThat(cats).hasSize(1);
        assertThat(cats.get(0).isHasNoticeImages()).isTrue();
        assertThat(cats.get(0).isHasItems()).isFalse();
    }

    @Test
    void plainLinesBecomeOfficialMd() {
        EventStop stop = new EventStop();
        stop.setMdList("응원봉\n슬로건|10000");

        List<EventStop.MdCategory> cats = stop.getMdCategories();
        assertThat(cats).hasSize(1);
        assertThat(cats.get(0).getTitle()).isEqualTo("Official MD");
        assertThat(cats.get(0).getItems()).hasSize(2);
    }
}

package com.example.triplesweb.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventStopSetlistParseTest {

    @Test
    void parsesSectionAndSongRowsWithAlbumAndNote() {
        EventStop stop = new EventStop();
        stop.setSetlist(String.join("\n",
                "# VCR 1 : OPENING VCR",
                "Baby Flower|LOVE&POP pt.1",
                "Bionic Power|Performante|24인조",
                "MENT 1 : 단체 인사",
                "Love2Love|ASSEMBLE25|",
                "추리소설"));

        List<EventStop.SetlistRow> rows = stop.getSetlistRows();
        assertThat(rows).hasSize(6);

        assertThat(rows.get(0).isSection()).isTrue();
        assertThat(rows.get(0).getSectionLabel()).isEqualTo("VCR 1 : OPENING VCR");

        assertThat(rows.get(1).isSection()).isFalse();
        assertThat(rows.get(1).getOrderNum()).isEqualTo(1);
        assertThat(rows.get(1).getTitle()).isEqualTo("Baby Flower");
        assertThat(rows.get(1).getSubtitle()).isEqualTo("LOVE&POP pt.1");
        assertThat(rows.get(1).isHasNote()).isFalse();

        assertThat(rows.get(2).getOrderNum()).isEqualTo(2);
        assertThat(rows.get(2).getNote()).isEqualTo("24인조");

        assertThat(rows.get(3).isSection()).isTrue();
        assertThat(rows.get(3).getSectionLabel()).isEqualTo("MENT 1 : 단체 인사");

        assertThat(rows.get(4).getOrderNum()).isEqualTo(3);
        assertThat(rows.get(4).getTitle()).isEqualTo("Love2Love");
        assertThat(rows.get(4).isHasNote()).isFalse();

        assertThat(rows.get(5).getOrderNum()).isEqualTo(4);
        assertThat(rows.get(5).getTitle()).isEqualTo("추리소설");
        assertThat(rows.get(5).isHasSubtitle()).isFalse();
    }

    @Test
    void plainSongLinesRemainCompatible() {
        EventStop stop = new EventStop();
        stop.setSetlist("Rising\nGirls Never Die");

        List<EventStop.SetlistRow> rows = stop.getSetlistRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getOrderNum()).isEqualTo(1);
        assertThat(rows.get(0).getTitle()).isEqualTo("Rising");
        assertThat(rows.get(1).getOrderNum()).isEqualTo(2);
        assertThat(rows.get(1).getTitle()).isEqualTo("Girls Never Die");
    }
}

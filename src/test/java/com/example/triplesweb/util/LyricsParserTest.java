package com.example.triplesweb.util;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.LyricsParser.Line;
import com.example.triplesweb.util.LyricsParser.Part;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LyricsParserTest {

    @Test
    void recognizesOnlyExactNamesAndInheritsAcrossStanzas() {
        var result = LyricsParser.parse(
                "Intro: listen\r\nALL： 함께\r\n김채연: Go: now\r\n다음 줄\r\n \r\n"
                        + "김채연,미등록: 그대로\r\n김채연,: 그대로\r\n김: 그대로",
                List.of(artist(4L, "김채연", "#98f21d")));

        assertThat(result.stanzas()).containsExactly(
                List.of(new Line("Intro: listen", null, null), new Line("함께", null, null),
                        new Line("Go: now", "#98f21d", null), new Line("다음 줄", "#98f21d", null)),
                List.of(new Line("김채연,미등록: 그대로", "#98f21d", null),
                        new Line("김채연,: 그대로", "#98f21d", null),
                        new Line("김: 그대로", "#98f21d", null)));
        assertThat(result.legend()).containsExactly(new Part("ALL", null), new Part("김채연", "#98f21d"));
    }

    @Test
    void buildsGradientWithValidatedColorsAndOrdersOnlyUsedPartsInLegend() {
        var result = LyricsParser.parse("이지우, 정혜린: 같이\nALL: 다 같이", List.of(
                artist(3L, "이지우", "#Aa02Cc"),
                artist(2L, "정혜린", "#9200ff; color:red"),
                artist(1L, "윤서연", "#22aeff")));

        assertThat(result.stanzas()).containsExactly(List.of(
                new Line("같이", null, "linear-gradient(90deg, #Aa02Cc, #e3e2e8)"),
                new Line("다 같이", null, null)));
        assertThat(result.legend()).containsExactly(
                new Part("ALL", null), new Part("정혜린", "#e3e2e8"), new Part("이지우", "#Aa02Cc"));
    }

    @Test
    void allResetsMemberColorAndDoesNotSupplyAGradientColor() {
        var result = LyricsParser.parse("ALL,김채연: 함께\nALL: 다시\n이어지는 줄",
                List.of(artist(4L, "김채연", "#98f21d")));

        assertThat(result.stanzas()).containsExactly(List.of(new Line("함께", "#98f21d", null),
                new Line("다시", null, null), new Line("이어지는 줄", null, null)));
    }

    @Test
    void blankTextAndUnusedPartMarkersDoNotProduceStanzasOrLegend() {
        var result = LyricsParser.parse("\nALL:  \n\n", List.of());

        assertThat(result.stanzas()).isEmpty();
        assertThat(result.legend()).isEmpty();
    }

    private static Artist artist(long id, String name, String color) {
        Artist artist = new Artist();
        artist.setArtistId(id);
        artist.setName(name);
        artist.setColor(color);
        return artist;
    }
}

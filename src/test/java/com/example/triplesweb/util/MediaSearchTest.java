package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;

class MediaSearchTest {
    @Test
    void matchesKoreanAndLowercaseRegardlessOfSpacingAndCase() {
        String title = "WAV 타! 서연&지연이랑 드라이브 가자";
        assertThat(MediaSearch.matches(title, "드라이브")).isTrue();
        assertThat(MediaSearch.matches(title, "  드라이브  ")).isTrue();
        assertThat(MediaSearch.matches("tripleS 'Baby Flower' Official MV", "baby flower")).isTrue();
        assertThat(MediaSearch.matches("tripleS 'Baby Flower' Official MV", "BABY")).isTrue();
        assertThat(MediaSearch.matches(title, "없는제목")).isFalse();
    }

    @Test
    void matchesDecomposedHangulAgainstComposedTitle() {
        String composed = "드라이브";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo(composed);
        assertThat(MediaSearch.matches("서연이랑 드라이브 가자", decomposed)).isTrue();
    }
}

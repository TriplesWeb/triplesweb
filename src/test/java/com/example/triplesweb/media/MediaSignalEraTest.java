package com.example.triplesweb.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaSignalEraTest {
    @Test
    void splitsSignalByHausEra() {
        assertThat(MediaSignalEra.from("2022-11-30", "SIGNAL 221130")).isEqualTo(MediaSignalEra.GAPYEONG);
        assertThat(MediaSignalEra.from("2023-07-02", "SIGNAL")).isEqualTo(MediaSignalEra.SEONGSU);
        assertThat(MediaSignalEra.from("2024-05-10", "SIGNAL")).isEqualTo(MediaSignalEra.ALL_HAUS);
        assertThat(MediaSignalEra.from("2024-05-11", "SIGNAL")).isEqualTo(MediaSignalEra.DEBUT24);
        assertThat(MediaSignalEra.from(null, "되고파 너의 하연 Behind | SIGNAL 221027"))
                .isEqualTo(MediaSignalEra.GAPYEONG);
        assertThat(MediaSignalEra.matches("2023-01-01", "SIGNAL", "seongsu")).isTrue();
        assertThat(MediaSignalEra.matches("2023-01-01", "SIGNAL", "gapyeong")).isFalse();
    }
}

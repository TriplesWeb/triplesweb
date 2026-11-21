package com.example.triplesweb.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTabClassifierTest {
    @Test
    void classifiesOfficialSeriesBeforeMv() {
        assertThat(MediaTabClassifier.tabFor("tripleS SIGNAL #100")).isEqualTo("signal");
        assertThat(MediaTabClassifier.tabFor("BEHIND the scene")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor("비하인드 캠")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor("Secret Base #12")).isEqualTo("secret");
        assertThat(MediaTabClassifier.tabFor("시크릿베이스")).isEqualTo("secret");
    }

    @Test
    void classifiesMusicVideosAndFallsBackToContents() {
        assertThat(MediaTabClassifier.tabFor("tripleS(트리플에스) 'Andless' Official MV")).isEqualTo("mv");
        assertThat(MediaTabClassifier.tabFor("tripleS 'Girls Never Die' M/V")).isEqualTo("mv");
        assertThat(MediaTabClassifier.tabFor("Dance Practice")).isEqualTo("contents");
        assertThat(MediaTabClassifier.tabFor(null)).isEqualTo("contents");
        assertThat(MediaTabClassifier.tabFor("")).isEqualTo("contents");
    }
}

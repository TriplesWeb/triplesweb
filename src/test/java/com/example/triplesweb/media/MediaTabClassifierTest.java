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
    void classifiesSignalLiveAsContents() {
        assertThat(MediaTabClassifier.tabFor("캠퍼스 여신 | SIGNAL LIVE 261120", "18:26")).isEqualTo("contents");
        assertThat(MediaTabClassifier.tabFor("tripleS SIGNAL LIVE", "10:00")).isEqualTo("contents");
    }

    @Test
    void classifiesVerifiedShortsWithoutUsingDurationOrHashtags() {
        assertThat(MediaTabClassifier.tabFor("'ANDLESS' in Chicago endless memories", "0:57")).isEqualTo("contents");
        assertThat(MediaTabClassifier.tabFor("서연 SIGNAL", "0:40")).isEqualTo("signal");
        assertThat(MediaTabClassifier.tabFor("tripleS 'Andless' Official MV", "0:30")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor("서연 SIGNAL", "2:15", true, false, false, false, true))
                .isEqualTo("shorts");
        assertThat(MediaTabClassifier.tabFor(
                "트리플에스가 말아주는 폭풍 칼군무 퍼포먼스 #MCOUNTDOWN #엠카운트다운 #tripleS",
                "1:39", false, true)).isEqualTo("music");
        assertThat(MediaTabClassifier.tabFor("tripleS - Baby Flower 260614 @ INKIGAYO", "3:24", false, true))
                .isEqualTo("music");
    }

    @Test
    void classifiesMusicVideosAndFallsBackToContents() {
        assertThat(MediaTabClassifier.tabFor("괜찮겠어? tripleS ‘Andless’ MV Reaction")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor("tripleS(트리플에스) 'Andless' Official MV")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor(
                "tripleS(트리플에스) 'Andless' Official MV", null, true, false, true, false, false))
                .isEqualTo("mv");
        assertThat(MediaTabClassifier.tabFor(
                "‘Door’ MV 리액션 | EN JP CN | Aria.SSS", null, true, false, true, false, false))
                .isEqualTo("behind");
        assertThat(MediaTabClassifier.correctMvTab("mv", "Girls Never Die MV ep.02")).isEqualTo("behind");
        assertThat(MediaTabClassifier.correctMvTab("mv", "tripleS Aria ‘Door’ MV ")).isEqualTo("mv");
        assertThat(MediaTabClassifier.tabFor("tripleS 'Girls Never Die' M/V")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor("되고파 너의 하연 Behind | SIGNAL 261027")).isEqualTo("behind");
        assertThat(MediaTabClassifier.tabFor("Dance Practice")).isEqualTo("contents");
        assertThat(MediaTabClassifier.tabFor(null)).isEqualTo("contents");
        assertThat(MediaTabClassifier.tabFor("")).isEqualTo("contents");
    }

    @Test
    void classifiesExternalAndFancamAfterShorts() {
        assertThat(MediaTabClassifier.tabFor("GAP CRUSH #tripleS", "4:12", false)).isEqualTo("external");
        assertThat(MediaTabClassifier.tabFor("tripleS 'Andless' Official MV", "3:20", false)).isEqualTo("external");
        assertThat(MediaTabClassifier.tabFor("트리플에스 직캠 Rising", "3:11", false)).isEqualTo("fancam");
        assertThat(MediaTabClassifier.tabFor("[4K] tripleS EYE CONTACT CAM", "3:40", true)).isEqualTo("fancam");
        assertThat(MediaTabClassifier.tabFor("'ANDLESS' in Chicago", "0:57", false)).isEqualTo("external");
        assertThat(MediaTabClassifier.tabFor("Girls Never Die - tripleS - Music Bank", "3:12", false)).isEqualTo("music");
        assertThat(MediaTabClassifier.tabFor("트리플에스 직캠 Rising @ Music Bank", "3:11", false)).isEqualTo("fancam");
        assertThat(MediaTabClassifier.tabFor("tripleS Inkigayo", "3:40", false, true)).isEqualTo("music");
        assertThat(MediaTabClassifier.isOfficialChannel(
                "UCJnL-TBcsYrF2SLs7tmiC8Q", "tripleS official", "UCJnL-TBcsYrF2SLs7tmiC8Q")).isTrue();
        assertThat(MediaTabClassifier.isOfficialChannel(
                "UC1thekxxxxxxxxxxxxxxx", "1theK Originals", "UCJnL-TBcsYrF2SLs7tmiC8Q")).isFalse();
    }
}

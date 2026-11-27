package com.example.triplesweb.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaHashtagsTest {
    @Test
    void findsHashtagsHandlesAndKoreanName() {
        assertThat(MediaHashtags.mentionsTripleS("GAP CRUSH #tripleS", "")).isTrue();
        assertThat(MediaHashtags.mentions("안녕 @triples 팬이에요")).isTrue();
        assertThat(MediaHashtags.mentions("트리플에스 출연")).isTrue();
        assertThat(MediaHashtags.mentions("random vlog")).isFalse();
        assertThat(MediaHashtags.channelNameEndsWithOfficial("1theK Official")).isTrue();
        assertThat(MediaHashtags.channelNameEndsWithOfficial("SBS KPOP ZOOM")).isFalse();
    }

    @Test
    void keepsOnlyKnownSourcesNotRandomHashtagVideos() {
        var handles = java.util.List.of("1theKOriginals", "SBSKPOP", "KBSKpop");
        assertThat(MediaHashtags.keepKnownSource(
                "GAP CRUSH #tripleS", "", "UC1thekxxxxxxxxxxxxxxx", "1theK Originals",
                java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.keepKnownSource(
                "트리플에스 직캠 Rising", "직캠 #tripleS", "UCSBS", "SBS KPOP ZOOM",
                java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.keepKnownSource(
                "Girls Never Die - tripleS - Music Bank", "", "UCKBS", "KBS Kpop",
                java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.keepKnownSource(
                "stage clip #tripleS", "", "UCxxxx", "Studio Choom Official",
                java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.keepKnownSource(
                "내가 좋아하는 #tripleS 모음", "팬 편집", "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정",
                java.util.Set.of(), handles)).isFalse();
        assertThat(MediaHashtags.keepKnownSource(
                "Music Bank 다른 가수", "", "UCKBS", "KBS Kpop",
                java.util.Set.of(), handles)).isFalse();
        assertThat(MediaHashtags.keepKnownSource(
                "4K tripleS LYNN UNOFFICIAL FANCAM @MCOUNTDOWN", "",
                "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정",
                java.util.Set.of(), handles)).isFalse();
        assertThat(MediaHashtags.keepKnownSource(
                "tripleS Andless @MCOUNTDOWN", "",
                "UCmnetm2xxxxxxxxxxxxx", "M2",
                java.util.Set.of("UCmnetm2xxxxxxxxxxxxx"), java.util.List.of("Mnet", "MnetM2"))).isTrue();
        assertThat(MediaHashtags.keepKnownSource(
                "NCT WISH가 알려주는 이번 주 엠카운트다운 라인업은? #엠카운트다운 EP.950", "",
                "UCmnetm2xxxxxxxxxxxxx", "M2",
                java.util.Set.of("UCmnetm2xxxxxxxxxxxxx"), java.util.List.of("Mnet", "MnetM2"))).isFalse();
        assertThat(MediaHashtags.overlapsOtherKpopAct(
                "NCT WISH가 알려주는 이번 주 엠카운트다운 라인업은? #엠카운트다운 EP.950",
                "")).isTrue();
        assertThat(MediaHashtags.overlapsOtherKpopAct(
                "트리플에스가 말아주는 폭풍 칼군무 퍼포먼스 #MCOUNTDOWN #엠카운트다운 #tripleS",
                "")).isFalse();
        assertThat(MediaHashtags.taggedOtherKpopAct(
                "tripleS Andless #MCOUNTDOWN #알파드라이브원", "", List.of("서연", "SeoYeon"))).isTrue();
        assertThat(MediaHashtags.taggedOtherKpopAct(
                "tripleS Andless #MCOUNTDOWN #tripleS", "", List.of("서연", "SeoYeon"))).isFalse();
        assertThat(MediaHashtags.otherArtistOfficialShorts(
                "clip #tripleS #서연", "", "IVE Official", List.of("서연", "SeoYeon"))).isTrue();
        assertThat(MediaHashtags.otherArtistOfficialShorts(
                "clip #tripleS", "", "랜덤 팬 계정", List.of("서연"))).isFalse();
    }

    @Test
    void flagsFanMadeTitlesAndDescriptions() {
        assertThat(MediaHashtags.looksFanMade("트리플에스 팬메이드 모음", "")).isTrue();
        assertThat(MediaHashtags.looksFanMade("GAP CRUSH fanmade", "")).isTrue();
        assertThat(MediaHashtags.looksFanMade("직캠", "fan edit #tripleS")).isTrue();
        assertThat(MediaHashtags.looksFanMade(
                "4K tripleS LYNN UNOFFICIAL FANCAM @MCOUNTDOWN", "")).isTrue();
        assertThat(MediaHashtags.looksFanMade("Girls Never Die - Music Bank", "KBS")).isFalse();
    }

    @Test
    void flagsOffChannelMusicShowFancams() {
        var handles = java.util.List.of("1theKOriginals", "SBSKPOP", "Mnet", "MnetM2");
        assertThat(MediaHashtags.looksOffChannelBroadcast(
                "4K tripleS LYNN FANCAM @MCOUNTDOWN", "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정",
                java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.unknownUploader(
                "UCfanxxxxxxxxxxxxxxx", "랜덤 팬 계정", java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.looksOffChannelBroadcast(
                "tripleS - Baby Flower 260614 @ INKIGAYO", "UCfanxxxxxxxxxxxxxxx", "팬 업로드 계정",
                java.util.Set.of(), handles)).isTrue();
        assertThat(MediaHashtags.looksOffChannelBroadcast(
                "tripleS Andless M Countdown", "UCmnetm2xxxxxxxxxxxxx", "M2",
                java.util.Set.of("UCmnetm2xxxxxxxxxxxxx"), handles)).isFalse();
        assertThat(MediaHashtags.looksOffChannelBroadcast(
                "트리플에스 직캠 Rising", "UCSBS", "SBS KPOP ZOOM",
                java.util.Set.of(), handles)).isFalse();
    }
}

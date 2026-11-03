package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

class MemberPortraitOptionsTest {

    @Test
    void seoyeonMatchesSoloNameAndS1PairsButNotS10OrS11() {
        assertTrue(MemberPortraitOptions.objektIncludesMember("윤서연", "윤서연"));
        assertTrue(MemberPortraitOptions.objektIncludesMember("SeoYeon", "윤서연"));
        assertTrue(MemberPortraitOptions.objektIncludesMember("S1 X S9", "윤서연"));
        assertTrue(MemberPortraitOptions.objektIncludesMember("Cream02 S1 X S14 601A", "seoyeon"));
        assertFalse(MemberPortraitOptions.objektIncludesMember("S11 X S14", "윤서연"));
        assertFalse(MemberPortraitOptions.objektIncludesMember("Cream02 S10 X S19 601A", "윤서연"));
        assertFalse(MemberPortraitOptions.objektIncludesMember("S21", "S1"));
    }

    @Test void unitLabelsResolveToTheirMembersAndOtherLabelsDoNot() {
        assertThat(MemberPortraitOptions.objektMembers("S5 X S20")).containsExactly("김유연", "박시온");
        assertThat(MemberPortraitOptions.objektMembers("S5XS20")).containsExactly("김유연", "박시온");
        assertThat(MemberPortraitOptions.objektMembers("S1 X S10")).containsExactly("윤서연", "서다현").doesNotContain("정혜린");
        assertThat(MemberPortraitOptions.objektMembers("Yooyeon X Shion")).containsExactly("김유연", "박시온");
        assertThat(MemberPortraitOptions.objektMembers("S21")).containsExactly("김채원");
        assertThat(MemberPortraitOptions.displayMember("JiYeon")).isEqualTo("지연");
        assertThat(MemberPortraitOptions.displayMember("S5 X S20")).isEqualTo("김유연 × 박시온");
        assertThat(MemberPortraitOptions.displayMember("zenith")).isEqualTo("zenith");
        assertThat(MemberPortraitOptions.koreanCollectionTitle("Cream02 Xinyu 356A")).isEqualTo("Cream02 신위 356A");
        assertThat(MemberPortraitOptions.koreanCollectionTitle("Cream02 SeoAh 101A")).isEqualTo("Cream02 서아 101A");
        assertThat(MemberPortraitOptions.koreanCollectionTitle("Cream02 HaYeon 367Z")).isEqualTo("Cream02 정하연 367Z");
        assertThat(MemberPortraitOptions.koreanCollectionTitle("Atom01 Kaede 001Z")).isEqualTo("Atom01 카에데 001Z");
        assertThat(MemberPortraitOptions.koreanCollectionTitle("Cream02 S1 X S14 601A")).isEqualTo("Cream02 S1 X S14 601A");
        assertThat(MemberPortraitOptions.koreanCollectionTitle("Cream02 신위 356A")).isEqualTo("Cream02 신위 356A");
        for (String other : java.util.List.of("AAA", "TokyoHaus", "sun", "+(KR)E", ""))
            assertThat(MemberPortraitOptions.objektMembers(other)).isEmpty();
    }

    @Test void findArtistUsesSoloColorFromEitherName() {
        var artist = new com.example.triplesweb.entity.Artist();
        artist.setArtistId(1L);
        artist.setName("윤서연");
        artist.setEngName("SeoYeon");
        artist.setColor("#111111");
        assertThat(MemberPortraitOptions.findArtist("S1", java.util.List.of(artist)).getArtistId()).isEqualTo(1L);
        assertThat(MemberPortraitOptions.artistColor("서연", java.util.List.of(artist))).isEqualTo("#111111");
        assertThat(MemberPortraitOptions.artistColor("없는멤버", java.util.List.of(artist))).isEqualTo("#00f5d4");
    }
}

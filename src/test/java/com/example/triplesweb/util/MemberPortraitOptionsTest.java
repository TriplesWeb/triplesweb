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

    @Test void earliestMemberIndexUsesSoloThenTheFirstUnitMember() {
        assertThat(MemberPortraitOptions.earliestMemberIndex("SeoYeon")).isZero();
        assertThat(MemberPortraitOptions.earliestMemberIndex("지연")).isEqualTo(23);
        assertThat(MemberPortraitOptions.earliestMemberIndex("S1 X S9")).isZero();
        assertThat(MemberPortraitOptions.earliestMemberIndex("S5 X S20")).isEqualTo(4);
        assertThat(MemberPortraitOptions.earliestMemberIndex("S24 X S1")).isZero();
        assertThat(MemberPortraitOptions.earliestMemberIndex("zenith")).isEqualTo(24);
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

    @Test void objektMemberLogoCoversS1ThroughS24SoloCards() {
        assertThat(MemberPortraitOptions.objektMemberLogo("윤서연")).isEqualTo("/images/objekt-member-logos/s01.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("SeoYeon")).isEqualTo("/images/objekt-member-logos/s01.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("S1")).isEqualTo("/images/objekt-member-logos/s01.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("서다현")).isEqualTo("/images/objekt-member-logos/s10.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("코토네")).isEqualTo("/images/objekt-member-logos/s11.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("Kotone")).isEqualTo("/images/objekt-member-logos/s11.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("S11")).isEqualTo("/images/objekt-member-logos/s11.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("설린")).isEqualTo("/images/objekt-member-logos/s22.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("지연")).isEqualTo("/images/objekt-member-logos/s24.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("JiYeon")).isEqualTo("/images/objekt-member-logos/s24.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("S24")).isEqualTo("/images/objekt-member-logos/s24.png");
        assertThat(MemberPortraitOptions.objektMemberLogo("S1 X S9")).isEmpty();
        assertThat(MemberPortraitOptions.objektMemberLogo("S8 X S12")).isEmpty();
        assertThat(MemberPortraitOptions.objektMemberLogo("윤서연 X 기타")).isEmpty();
        assertThat(MemberPortraitOptions.objektMemberLogo("zenith")).isEmpty();
    }
}

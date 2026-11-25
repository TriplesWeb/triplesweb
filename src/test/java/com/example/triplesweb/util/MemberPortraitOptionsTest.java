package com.example.triplesweb.util;

import com.example.triplesweb.entity.Artist;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void fullLineupRequiresEverySoloMember() {
        assertThat(MemberPortraitOptions.coversFullLineup(MemberPortraitOptions.koreanOrder())).isTrue();
        assertThat(MemberPortraitOptions.coversFullLineup(MemberPortraitOptions.koreanOrder().subList(0, 23))).isFalse();
    }

    @Test void earliestMemberIndexUsesSoloThenTheFirstUnitMember() {
        assertThat(MemberPortraitOptions.earliestMemberIndex("SeoYeon")).isZero();
        assertThat(MemberPortraitOptions.earliestMemberIndex("지연")).isEqualTo(23);
        assertThat(MemberPortraitOptions.earliestMemberIndex("S1 X S9")).isZero();
        assertThat(MemberPortraitOptions.earliestMemberIndex("S5 X S20")).isEqualTo(4);
        assertThat(MemberPortraitOptions.earliestMemberIndex("S24 X S1")).isZero();
        assertThat(MemberPortraitOptions.earliestMemberIndex("zenith")).isEqualTo(24);
        assertThat(MemberPortraitOptions.earliestMemberIndex("지연, 윤서연")).isZero();
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

    @Test void presentsUnitColorsInMemberOrderAndKeepsTheRawMember() {
        var artists = List.of(artist("박시온", "#F58"), artist("윤서연", "#123456"), artist("공유빈", "#abcdef"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("member", "S1xS20 X S8");
        MemberPortraitOptions.addObjektMemberPresentation(row, artists);

        assertThat(row).containsEntry("member", "S1xS20 X S8")
                .containsEntry("memberDisplay", "윤서연 × 박시온 × 공유빈")
                .containsEntry("memberColors", List.of("#123456", "#F58", "#abcdef"))
                .containsEntry("memberMarkFill", "linear-gradient(135deg, #123456, #F58, #abcdef)")
                .containsEntry("memberMarkMask", "/images/objekt-member-logos/clef.png");
        assertThat(MemberPortraitOptions.displayMember("S1 x S20")).isEqualTo("윤서연 × 박시온");
        assertThat(MemberPortraitOptions.displayMember("S8 X S12")).isEqualTo("공유빈 × 곽연지");
        assertThat(MemberPortraitOptions.displayMember("S10 x S13")).isEqualTo("서다현 × 니엔");
        assertThat(MemberPortraitOptions.objektMemberColors("S01", artists)).containsExactly("#123456");
        assertThat(MemberPortraitOptions.objektMemberColors("zenith", artists)).isEmpty();
        assertThat(MemberPortraitOptions.objektMemberNumbers("S1 x S20")).containsExactly("s1", "s01", "s20");
        assertThat(MemberPortraitOptions.objektMemberNumbers("S10 x S13")).containsExactly("s10", "s13");
    }

    private static Artist artist(String name, String color) {
        var artist = new Artist();
        artist.setName(name);
        artist.setColor(color);
        return artist;
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

    @Test void soloCardsShareTheClefAndUseArtistColorOrTheDefault() {
        var artists = List.of(artist("윤서연", "#22AEFF"), artist("설린", "#7BBA8D"));
        for (String name : List.of("윤서연", "SeoYeon", "S1", "S01")) {
            Map<String, Object> row = new LinkedHashMap<>(Map.of("member", name));
            MemberPortraitOptions.addObjektMemberPresentation(row, artists);
            assertThat(row).containsEntry("memberMarkMask", "/images/objekt-member-logos/clef.png")
                    .containsEntry("memberMarkFill", "#22AEFF");
        }
        for (String name : MemberPortraitOptions.koreanOrder()) {
            Map<String, Object> row = new LinkedHashMap<>(Map.of("member", name));
            MemberPortraitOptions.addObjektMemberPresentation(row, List.of());
            assertThat(row).containsEntry("memberMarkMask", "/images/objekt-member-logos/clef.png")
                    .containsEntry("memberMarkFill", "#00f5d4");
        }
        Map<String, Object> other = new LinkedHashMap<>(Map.of("member", "zenith"));
        MemberPortraitOptions.addObjektMemberPresentation(other, artists);
        assertThat(other).containsEntry("memberMarkMask", "").containsEntry("memberMarkFill", "");
    }

    @Test void archiveSymbolLogoMapsS1SeoyeonThroughS24Jiyeon() {
        assertThat(MemberPortraitOptions.archiveSymbolLogo("윤서연")).isEqualTo("/images/archive/member-logos/1.png");
        assertThat(MemberPortraitOptions.archiveSymbolLogo("서연")).isEqualTo("/images/archive/member-logos/1.png");
        assertThat(MemberPortraitOptions.archiveSymbolLogo("SeoYeon")).isEqualTo("/images/archive/member-logos/1.png");
        assertThat(MemberPortraitOptions.archiveSymbolLogo("설린")).isEqualTo("/images/archive/member-logos/22.png");
        assertThat(MemberPortraitOptions.archiveSymbolLogo("지연")).isEqualTo("/images/archive/member-logos/24.png");
        assertThat(MemberPortraitOptions.archiveSymbolLogo("S1 X S9")).isEmpty();
        assertThat(MemberPortraitOptions.archiveSymbolLogo("zenith")).isEmpty();
    }
}

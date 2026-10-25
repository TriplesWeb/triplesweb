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
        for (String other : java.util.List.of("AAA", "TokyoHaus", "sun", "+(KR)E", ""))
            assertThat(MemberPortraitOptions.objektMembers(other)).isEmpty();
    }
}

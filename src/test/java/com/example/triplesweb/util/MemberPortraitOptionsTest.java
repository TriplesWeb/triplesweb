package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

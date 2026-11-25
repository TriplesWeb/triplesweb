package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjektSeasonCodesTest {

    @Test
    void mapsKnownSeasonsToShortCodes() {
        assertEquals("a", ObjektSeasonCodes.code("Atom01"));
        assertEquals("b", ObjektSeasonCodes.code("Binary 01"));
        assertEquals("c", ObjektSeasonCodes.code("cream-01"));
        assertEquals("d", ObjektSeasonCodes.code("Divine01"));
        assertEquals("e", ObjektSeasonCodes.code("Ever 01"));
        assertEquals("aa", ObjektSeasonCodes.code("Atom02"));
        assertEquals("bb", ObjektSeasonCodes.code("Binary02"));
        assertEquals("cc", ObjektSeasonCodes.code("Cream 02"));
        assertEquals("", ObjektSeasonCodes.code("Unknown"));
    }

    @Test
    void buildsSearchTokensLikeCc401() {
        assertEquals("cc cc401", ObjektSeasonCodes.searchTokens("Cream02", "401A"));
        assertEquals("a a012", ObjektSeasonCodes.searchTokens("Atom 01", "012Z"));
        assertEquals("bb", ObjektSeasonCodes.searchTokens("Binary02", ""));
        assertEquals("", ObjektSeasonCodes.searchTokens("Welcome", "001Z"));
        assertEquals("cc401", ObjektSeasonCodes.composite("Cream02", "401A"));
        assertEquals("SooMin 356A", ObjektSeasonCodes.cardTitle("Cream02 SooMin 356A", "Cream02"));
        assertEquals("S1 X S14 601A", ObjektSeasonCodes.cardTitle("Cream02 S1 X S14 601A", "Cream02"));
        assertEquals("Welcome", ObjektSeasonCodes.cardTitle("Welcome", "Cream02"));
        assertEquals("", ObjektSeasonCodes.cardTitle("", "Cream02"));
    }

    @Test
    void shortCodesMatchOnlyAsSeasonAliases() {
        String hayeon = "cream02 hayeon 401a double";
        assertTrue(ObjektSeasonCodes.matches("cc401", hayeon, "Cream02", "401A", "hayeon"));
        assertTrue(ObjektSeasonCodes.matches("cc", hayeon, "Cream02", "401A", "hayeon"));
        assertTrue(ObjektSeasonCodes.matches("a", "atom01 seoyeon 012z", "Atom01", "012Z", "seoyeon"));
        assertFalse(ObjektSeasonCodes.matches("a", hayeon, "Cream02", "401A", "hayeon"));
        assertTrue(ObjektSeasonCodes.matches("hayeon", hayeon, "Cream02", "401A", "hayeon"));
    }

    @Test
    void searchesMemberAliasesAndUsesExactNumberTokens() {
        String unit = "S1 x S20";
        for (String query : java.util.List.of("s1", "S01", "s20", "seoyeon", "윤서연", "서연", "박시온", "시온")) {
            assertTrue(ObjektSeasonCodes.matches(query, "Cream02 601A", "Cream02", "601A", unit), query);
        }
        assertFalse(ObjektSeasonCodes.matches("s1", "s1-in-slug", "Cream02", "609A", "S10 x S13"));
        assertFalse(ObjektSeasonCodes.matches("s1", "s1 in description", "Cream02", "609A", "S21"));
        assertFalse(ObjektSeasonCodes.matches("s1", "zenith s1", "s1", "601A", "zenith"));
        assertTrue(ObjektSeasonCodes.matches("채원", "Cream02 ChaeWon 286A", "Cream02", "286A", "ChaeWon"));
        assertTrue(ObjektSeasonCodes.matches("cc601", unit, "Cream02", "601A", unit));
    }
}

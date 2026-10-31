package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}

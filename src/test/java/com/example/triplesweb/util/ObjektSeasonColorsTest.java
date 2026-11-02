package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjektSeasonColorsTest {
    @Test void matchesSeasonChipPalette() {
        assertThat(ObjektSeasonColors.colorFor("Cream02")).isEqualTo("#FF8389");
        assertThat(ObjektSeasonColors.colorFor("Binary02")).isEqualTo("#83FF00");
        assertThat(ObjektSeasonColors.colorFor("Atom01")).isEqualTo("#FFDD00");
        assertThat(ObjektSeasonColors.colorFor("Divine01")).isEqualTo("#B400FF");
        assertThat(ObjektSeasonColors.colorFor("Ever01")).isEqualTo("#33ECFD");
        assertThat(ObjektSeasonColors.colorFor("cc")).isEqualTo("#FF8389");
        assertThat(ObjektSeasonColors.colorFor("전체")).isEqualTo("#A4B7B4");
    }
}

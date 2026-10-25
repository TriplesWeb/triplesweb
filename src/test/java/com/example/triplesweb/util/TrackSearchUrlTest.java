package com.example.triplesweb.util;

import com.example.triplesweb.entity.Track;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackSearchUrlTest {

    @Test
    void encodesPlusAndSpecialCharactersInSearchUrls() {
        Track track = new Track();
        track.setTitle("+(82)");
        assertThat(track.getYoutubeSearchUrl())
                .contains("search_query=")
                .contains("%2B")
                .doesNotContain("q=+(82)");
        assertThat(track.getMelonSearchUrl())
                .contains("q=")
                .contains("%2B%2882%29");
    }
}

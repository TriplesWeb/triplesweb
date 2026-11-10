package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObjektMotionMediaTest {
    @Test void keepsExistingFrontMedia() {
        assertThat(ObjektMotionMedia.resolve(Map.of(
                "class", "Motion",
                "slug", "cream02-kaede-504z",
                "frontMedia", "https://example.com/a.mp4"
        ))).isEqualTo("https://example.com/a.mp4");
    }

    @Test void usesApolloCdnWhenMotionHasNoFrontMedia() {
        assertThat(ObjektMotionMedia.resolve(Map.of(
                "class", "Motion",
                "slug", "cream02-kaede-504z",
                "artist", "tripleS"
        ))).isEqualTo("https://cdn.apollo.cafe/mco/triples/cream02-kaede-504z.mp4");
    }

    @Test void slugifiesCollectionIdWhenSlugIsMissing() {
        assertThat(ObjektMotionMedia.resolve(Map.of(
                "classKey", "motion",
                "collectionId", "Cream02 Kaede 504Z"
        ))).isEqualTo("https://cdn.apollo.cafe/mco/triples/cream02-kaede-504z.mp4");
    }

    @Test void skipsNonMotionCards() {
        assertThat(ObjektMotionMedia.resolve(Map.of(
                "class", "Special",
                "slug", "cream02-hayeon-367z"
        ))).isEmpty();
    }
}

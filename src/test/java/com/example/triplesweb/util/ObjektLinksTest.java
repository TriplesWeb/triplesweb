package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObjektLinksTest {
    @Test void uuidSlugStillBuildsSeasonMemberNumberKey() {
        assertThat(ObjektLinks.key(Map.of(
                "id", "01a17092-abe1-72c0-925d-d7058df81a1f",
                "slug", "01a17092-abe1-72c0-925d-d7058df81a1f",
                "member", "HaYeon",
                "season", "Cream02",
                "collectionNo", "368Z"
        ))).isEqualTo("hayeoncc368z");
    }

    @Test void indexSlugWithoutMemberFieldKeepsExistingPublicAlias() {
        assertThat(ObjektLinks.key(Map.of(
                "slug", "cream02-hayeon-367z",
                "season", "Cream02",
                "collectionNo", "367Z"
        ))).isEqualTo("hayeoncc367z");
    }

    @Test void uuidIsNotUsedAsPublicKey() {
        String uuid = "01a17092-abe1-72c0-925d-d7058df81a1f";
        assertThat(ObjektLinks.key(Map.of("id", uuid, "slug", uuid))).isEqualTo(uuid);
        assertThat(ObjektLinks.key(Map.of("id", uuid, "slug", uuid))).doesNotContain("hayeon");
    }
}

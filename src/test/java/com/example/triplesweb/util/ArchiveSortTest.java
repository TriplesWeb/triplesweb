package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveSortTest {
    @Test
    void fromNormalizesKnownKeys() {
        assertThat(ArchiveSort.from(null)).isEqualTo(ArchiveSort.NEWEST);
        assertThat(ArchiveSort.from("")).isEqualTo(ArchiveSort.NEWEST);
        assertThat(ArchiveSort.from("oldest")).isEqualTo(ArchiveSort.OLDEST);
        assertThat(ArchiveSort.from("TITLE")).isEqualTo(ArchiveSort.TITLE);
        assertThat(ArchiveSort.from("first-member")).isEqualTo(ArchiveSort.FIRST_MEMBER);
        assertThat(ArchiveSort.from("last-member")).isEqualTo(ArchiveSort.LAST_MEMBER);
        assertThat(ArchiveSort.from("unknown")).isEqualTo(ArchiveSort.NEWEST);
    }

    @Test
    void keyRoundTrips() {
        for (ArchiveSort sort : ArchiveSort.values()) {
            assertThat(ArchiveSort.from(sort.key())).isEqualTo(sort);
        }
    }
}

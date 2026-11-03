package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolygonMintTotalsTest {
    @Test void classpathSummaryMatchesScanTotals() {
        var snapshot = PolygonMintTotals.load(new ObjectMapper());
        assertThat(snapshot.triples()).isEqualTo(5_705_174L);
        assertThat(snapshot.artms()).isEqualTo(1_487_882L);
        assertThat(snapshot.total()).isEqualTo(7_193_056L);
        assertThat(snapshot.triples() + snapshot.artms()).isEqualTo(snapshot.total());
        assertThat(snapshot.lastMintBlock()).isEqualTo(70_361_022L);
        assertThat(snapshot.caption()).isEqualTo("Polygon 민트 7,193,056 · tripleS 5,705,174 · ARTMS 1,487,882");
    }
}

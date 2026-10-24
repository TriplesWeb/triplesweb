package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CosmoAccountServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void readsOwnedObjektsAndScannedSerials() throws Exception {
        var owned = mapper.readTree("""
                {"objekts":[{"collectionId":"101Z","member":"SeoYeon","class":"First","thumbnailImage":"https://cdn.example/a.jpg","tokenId":"9","serial":"ODMQ1"}]}
                """);
        var scans = mapper.readTree("""
                {"scans":[{"serialNumber":"SCAN-2","collectionId":"101Z","scannedAt":"2026-10-01"}]}
                """);

        assertThat(CosmoAccountService.extractObjekts(owned)).singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("collectionId", "101Z");
                    assertThat(row).containsEntry("serial", "ODMQ1");
                    assertThat(row).containsEntry("hasImage", true);
                });
        assertThat(CosmoAccountService.extractScans(scans)).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("serial", "SCAN-2"));
    }
}

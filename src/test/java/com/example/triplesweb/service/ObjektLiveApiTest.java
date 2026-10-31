package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;

/** Explicit live smoke test: no credentials or account snapshots. */
@EnabledIfEnvironmentVariable(named = "OBJEKT_LIVE_TEST", matches = "true")
class ObjektLiveApiTest {
    @Test void publicActivityPaginationAndCatalogMatchProductionAdapters() throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                verifyPublicApis();
                return;
            } catch (RestClientException failure) {
                Throwable cause = failure;
                while (cause != null && !(cause instanceof IOException)) cause = cause.getCause();
                // 연결 종료·시간 초과만 한정 재시도한다. HTTP 오류와 검증 실패는 그대로 실패한다.
                if (cause == null || attempt >= 3) throw failure;
                System.out.println("Live API transport retry " + attempt + "/2: " + cause.getClass().getSimpleName());
                Thread.sleep(attempt * 500L);
            }
        }
    }

    private void verifyPublicApis() {
        var client = new ObjektActivityClient("https://objekt.top/api/v1");
        var first = client.fetch(null);
        assertThat(first.events()).isNotEmpty();
        assertThat(first.next()).isNotNull();
        var second = client.fetch(first.next());
        assertThat(second.events()).isNotEmpty();
        var ids = new HashSet<String>();
        first.events().forEach(e -> assertThat(ids.add(e.id())).isTrue());
        second.events().forEach(e -> assertThat(ids.add(e.id())).isTrue());
        var http = RestClient.create("https://objekt.top/api/v1");
        JsonNode catalog = http.get().uri("/collections?artist=triples").retrieve().body(JsonNode.class);
        assertThat(catalog).isNotNull();
        assertThat(catalog.path("collections").isArray()).isTrue();
        assertThat(catalog.path("collections").size()).isPositive();
        JsonNode target = null;
        for (JsonNode item : catalog.path("collections")) if (item.path("id").asText().equals("01a170ae-1059-73ce-a7c5-7483cb19e656")) target = item;
        assertThat(target).as("Requested HaYeon collection must exist").isNotNull();
        System.out.println("Requested collection readable slug=" + target.path("slug").asText());
        var publicKeys = new java.util.HashSet<String>();
        for (JsonNode item : catalog.path("collections")) {
            String key = com.example.triplesweb.util.ObjektLinks.key(java.util.Map.of("slug",item.path("slug").asText(),"season",item.path("season").asText(),"collectionNo",item.path("collectionNo").asText()));
            assertThat(publicKeys.add(key)).as("Public detail URL must be unique: %s",key).isTrue();
        }
        String publicKey = com.example.triplesweb.util.ObjektLinks.key(java.util.Map.of("slug",target.path("slug").asText(),"season",target.path("season").asText(),"collectionNo",target.path("collectionNo").asText()));
        assertThat(publicKey).isEqualTo("hayeoncc367z");
        System.out.println("Requested public detail key="+publicKey+", unique detail URLs="+publicKeys.size());
        var market = new ObjektMarketDataService("https://objekt.top/api/v1", "https://api.cosmo.fans", "https://api.mainnet.abs.xyz", "unused");
        var lookup = market.serialLookup(java.util.Map.of("slug", target.path("slug").asText()), 1, 0);
        assertThat(lookup).containsEntry("status", "available");
        System.out.println("Live combined serial lookup: status=" + lookup.get("status") + ", owner=" + ((java.util.Map<?,?>)lookup.get("owner")).get("status"));
        JsonNode rarity = http.get().uri("/collections/rarity").retrieve().body(JsonNode.class);
        assertThat(rarity).isNotNull();
        assertThat(rarity.isArray()).isTrue();
        assertThat(rarity.size()).isPositive();
        JsonNode serials=http.get().uri("/collections/{slug}/serials",target.path("slug").asText()).retrieve().body(JsonNode.class);
        var supply=ObjektSerialSupplyService.analyze(serials,System.currentTimeMillis());
        assertThat(supply.count()).isPositive();
        long last=supply.ranges().get(supply.ranges().size()-1).last();
        JsonNode lastHistory=http.get().uri("/collections/{slug}/serials/{serial}/transfers",target.path("slug").asText(),last).retrieve().body(JsonNode.class);
        assertThat(lastHistory.path("tokenId").asText()).matches("[0-9]+");
        System.out.println("Live issued serials: unique="+supply.count()+", maximum="+last+", actual last token=OK");
        var event = first.events().get(0);
        JsonNode metadata = RestClient.create("https://api.cosmo.fans").get()
                .uri("/bff/v3/objekts/nft-metadata/{id}", event.tokenId()).retrieve().body(JsonNode.class);
        assertThat(metadata).isNotNull();
        assertThat(metadata.path("attributes").isArray()).isTrue();
        System.out.println("Live public API validation: activity pages=" + first.events().size() + "+" + second.events().size()
                + ", collections=" + catalog.path("collections").size() + ", rarity=" + rarity.size() + ", COSMO metadata=OK");
    }
}

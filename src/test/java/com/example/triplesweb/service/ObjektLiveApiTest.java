package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;

/** Explicit live smoke test: no credentials or account snapshots. */
@EnabledIfEnvironmentVariable(named = "OBJEKT_LIVE_TEST", matches = "true")
class ObjektLiveApiTest {
    @Test void publicActivityPaginationAndCatalogMatchProductionAdapters() {
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
        JsonNode rarity = http.get().uri("/collections/rarity").retrieve().body(JsonNode.class);
        assertThat(rarity).isNotNull();
        assertThat(rarity.isArray()).isTrue();
        assertThat(rarity.size()).isPositive();
        var event = first.events().get(0);
        JsonNode metadata = RestClient.create("https://api.cosmo.fans").get()
                .uri("/bff/v3/objekts/nft-metadata/{id}", event.tokenId()).retrieve().body(JsonNode.class);
        assertThat(metadata).isNotNull();
        assertThat(metadata.path("attributes").isArray()).isTrue();
        System.out.println("Live public API validation: activity pages=" + first.events().size() + "+" + second.events().size()
                + ", collections=" + catalog.path("collections").size() + ", rarity=" + rarity.size() + ", COSMO metadata=OK");
    }
}

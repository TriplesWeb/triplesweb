package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Polygon-era mint totals from the offline scan summary.
 * Does not read the 1.6GB JSONL. Not shown on /cosmo/objekt head; living card supply stays on objekt.top.
 */
@Service
public class PolygonMintTotals {
    public record Snapshot(long triples, long artms, long total, long lastMintBlock, String caption) {}

    private final Snapshot snapshot;

    public PolygonMintTotals(ObjectMapper mapper) {
        this.snapshot = load(mapper);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    static Snapshot load(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("data/polygon-mints.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            long triples = root.path("triples").asLong(-1);
            long artms = root.path("artms").asLong(-1);
            long total = root.path("total").asLong(-1);
            long lastMintBlock = root.path("lastMintBlock").asLong(-1);
            if (triples < 0 || artms < 0 || total < 0 || lastMintBlock < 0 || triples + artms != total) {
                throw new IllegalStateException("polygon-mints.json totals are incomplete or inconsistent");
            }
            NumberFormat format = NumberFormat.getIntegerInstance(Locale.KOREA);
            String caption = "발행량 " + format.format(total) + "개 · Polygon 민트"
                    + " · tripleS " + format.format(triples)
                    + " · ARTMS " + format.format(artms);
            return new Snapshot(triples, artms, total, lastMintBlock, caption);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read data/polygon-mints.json", ex);
        }
    }
}

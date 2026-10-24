package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads indexed Objekt ownership and fills missing card metadata on demand. */
@Service
public class AbstractObjektReadService {
    private static final Logger log = LoggerFactory.getLogger(AbstractObjektReadService.class);
    private static final String ZERO = "0x0000000000000000000000000000000000000000";
    private final JdbcTemplate jdbc;
    private final RestClient metadata;

    public AbstractObjektReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.metadata = RestClient.builder().baseUrl("https://api.cosmo.fans").build();
    }

    public List<Map<String, Object>> owned(String address, int limit) {
        if (address == null || address.isBlank()) return List.of();
        try {
            List<Long> ids = jdbc.query("""
                    SELECT token_id FROM abstract_objekt_owner
                    WHERE owner_address = ? AND owner_address <> ?
                    ORDER BY order_key DESC
                    LIMIT ?
                    """, (rs, row) -> rs.getLong(1), address.toLowerCase(), ZERO, limit);
            fillMissing(ids);
            if (ids.isEmpty()) return List.of();
            return jdbc.query("""
                    SELECT o.token_id, m.name, m.image, m.member, m.season, m.objekt_class, m.artist
                    FROM abstract_objekt_owner o
                    LEFT JOIN abstract_objekt_meta m ON m.token_id = o.token_id
                    WHERE o.token_id IN (%s)
                    ORDER BY o.order_key DESC
                    """.formatted(placeholders(ids.size())), (rs, row) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                long tokenId = rs.getLong("token_id");
                String name = rs.getString("name");
                String image = GravityService.safeImage(rs.getString("image") == null ? "" : rs.getString("image"));
                item.put("collectionId", name == null || name.isBlank() ? String.valueOf(tokenId) : name);
                item.put("member", text(rs.getString("member")));
                item.put("season", text(rs.getString("season")));
                item.put("objektClass", text(rs.getString("objekt_class")));
                item.put("tokenId", String.valueOf(tokenId));
                item.put("objektNo", "");
                item.put("serial", "");
                item.put("thumbnailImage", image);
                item.put("hasImage", !image.isBlank());
                return item;
            }, ids.toArray());
        } catch (RuntimeException ex) {
            log.warn("Indexed Objekt read failed: {}", ex.toString());
            return List.of();
        }
    }

    public long ownedCount(String address) {
        if (address == null || address.isBlank()) return 0L;
        try {
            Long count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM abstract_objekt_owner
                    WHERE owner_address = ? AND owner_address <> ?
                    """, Long.class, address.toLowerCase(), ZERO);
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    public String progressNote() {
        try {
            Long next = jdbc.query("SELECT next_block FROM abstract_index_state WHERE id = 1",
                    rs -> rs.next() ? rs.getLong(1) : null);
            if (next == null) return "Abstract 전송 수집 테이블이 아직 없습니다.";
            return "Abstract 전송 수집 커서 " + next + "블록. 이 블록 이전의 보유 카드부터 순서대로 쌓입니다.";
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void fillMissing(List<Long> ids) {
        if (ids.isEmpty()) return;
        List<Long> known = jdbc.query("SELECT token_id FROM abstract_objekt_meta WHERE token_id IN (" + placeholders(ids.size()) + ")",
                (rs, row) -> rs.getLong(1), ids.toArray());
        for (Long id : ids) {
            if (known.contains(id)) continue;
            remember(id);
        }
    }

    private void remember(long tokenId) {
        try {
            JsonNode meta = metadata.get().uri("/bff/v3/objekts/nft-metadata/" + tokenId)
                    .header("Accept", "application/json")
                    .retrieve().body(JsonNode.class);
            if (meta == null || !meta.isObject()) return;
            jdbc.update("""
                    INSERT INTO abstract_objekt_meta (token_id, name, image, member, season, objekt_class, artist)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE name = VALUES(name), image = VALUES(image), member = VALUES(member),
                        season = VALUES(season), objekt_class = VALUES(objekt_class), artist = VALUES(artist)
                    """, tokenId, meta.path("name").asText(""), meta.path("image").asText(""),
                    trait(meta, "Member"), trait(meta, "Season"), trait(meta, "Class"), trait(meta, "Artist"));
        } catch (RuntimeException ex) {
            log.debug("Objekt metadata {} skipped: {}", tokenId, ex.toString());
        }
    }

    private static String trait(JsonNode meta, String type) {
        for (JsonNode attribute : meta.path("attributes")) {
            if (type.equalsIgnoreCase(attribute.path("trait_type").asText())) return attribute.path("value").asText("");
        }
        return "";
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}

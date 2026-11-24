package com.example.triplesweb.service;

import com.example.triplesweb.dto.ObjektCatalogDto;
import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.infra.GzipRestClients;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Indexes issuance and retirement events without storing transfers between collectors. */
@Service
@ConditionalOnProperty(name = "objekt.mints.chain-enabled", havingValue = "true")
public class ObjektChainMintService {
    private static final Logger log = LoggerFactory.getLogger(ObjektChainMintService.class);
    private static final String NULL = "0x" + "0".repeat(40);
    private static final String TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate transactions;
    private final ObjektService catalog;
    private final ObjektCatalogRepository repository;
    private final ObjektMarketDataService market;
    private final RestClient rpc;
    private final String contract;
    private final String spin;
    private final String chainId;
    private final int confirmations;
    private final int blockBatch;
    private boolean loaded;
    private boolean chainVerified;

    public ObjektChainMintService(JdbcTemplate jdbc, TransactionTemplate transactions, ObjektService catalog,
                                  ObjektCatalogRepository repository, ObjektMarketDataService market,
                                  @Value("${objekt.mints.rpc-url}") String rpcUrl,
                                  @Value("${objekt.mints.chain-id}") String chainId,
                                  @Value("${objekt.mints.contract}") String contract,
                                  @Value("${objekt.mints.spin-address}") String spin,
                                  @Value("${objekt.mints.confirmations:60}") int confirmations,
                                  @Value("${objekt.mints.block-batch:200}") int blockBatch) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.transactions = transactions;
        this.catalog = catalog;
        this.repository = repository;
        this.market = market;
        this.rpc = GzipRestClients.create(rpcUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        this.chainId = chainId;
        this.contract = contract.toLowerCase(Locale.ROOT);
        this.spin = spin.toLowerCase(Locale.ROOT);
        this.confirmations = Math.max(1, confirmations);
        this.blockBatch = Math.max(1, blockBatch);
    }

    @Scheduled(fixedDelayString = "${objekt.mints.chain-poll-ms:5000}", initialDelay = 1000,
            scheduler = "objektMintScheduler")
    void collect() {
        try {
            long next = cursor(false);
            if (!loaded && next >= 0) reloadView();
            if (!chainVerified) {
                if (!chainId.equals(Long.toString(quantity(call("eth_chainId", List.of()))))) {
                    throw new IllegalStateException("RPC 체인이 설정과 일치하지 않습니다.");
                }
                chainVerified = true;
            }
            if (next < 0) {
                initialize();
                reloadView();
                return;
            }
            long confirmed = quantity(call("eth_blockNumber", List.of())) - confirmations;
            if (next > confirmed) return;
            long end = Math.min(next + blockBatch - 1, confirmed);
            Batch batch = prepare(next, end);
            long expected = next;
            Commit result = transactions.execute(status -> store(expected, end, batch));
            if (result == null) {
                loaded = false;
                return;
            }
            if (result.newCollections() || result.newMints()) {
                loaded = false;
                if (result.newCollections()) catalog.reloadStoredCatalog();
                if (result.newMints()) publishCounts();
                loaded = true;
            }
        } catch (RuntimeException error) {
            log.warn("오브젝트 민팅 수집 재시도: {}", error.getMessage());
        }
    }

    @Scheduled(cron = "${objekt.mints.audit-cron:0 40 4 * * *}", zone = "Asia/Seoul",
            scheduler = "objektMintScheduler")
    void audit() {
        if (!loaded) return;
        try {
            List<ObjektCatalog> externalCatalog = catalog.fetchExternalCatalog();
            catalog.enrichChainBackImages(externalCatalog);
            Map<String, Long> external = market.fetchExternalMintCounts();
            int missing = 0;
            int different = 0;
            List<String> examples = new ArrayList<>();
            for (ObjektCatalog row : externalCatalog) {
                String slug = row.getSlug();
                Long local = market.mintCount(slug);
                Long observed = external.get(slug);
                boolean missingCollection = catalog.findByKey(slug) == null;
                if (missingCollection) missing++;
                if (observed != null && !observed.equals(local)) different++;
                if (examples.size() < 10 && (missingCollection || observed != null && !observed.equals(local))) {
                    examples.add(slug + " (집계 " + local + ", 외부 공급량 " + observed + ")");
                }
            }
            log.info("오브젝트 일일 대조: 카탈로그 누락 {}, 수량 차이 {}, 예시 {}", missing, different, examples);
        } catch (RuntimeException error) {
            log.warn("오브젝트 일일 대조 실패: {}", error.getMessage());
        }
    }

    private void initialize() {
        List<ObjektCatalog> externalCatalog = catalog.fetchExternalCatalog();
        Map<String, Long> externalCounts = market.fetchExternalMintCounts();
        Set<String> knownIds = new HashSet<>();
        Set<String> knownSlugs = new HashSet<>();
        for (Map<String, Object> item : catalog.findCollections("triples")) {
            knownIds.add(item.getOrDefault("collectionId", "").toString());
            knownSlugs.add(item.getOrDefault("slug", "").toString());
        }
        List<ObjektCatalog> missing = new ArrayList<>();
        for (ObjektCatalog row : externalCatalog) {
            String slug = row.getSlug();
            if (slug == null || slug.isBlank()) continue;
            if (knownIds.contains(row.getCollectionId())) continue;
            knownIds.add(row.getCollectionId());
            if (knownSlugs.add(slug)) missing.add(row);
        }
        List<Map.Entry<String, Long>> counts = externalCounts.entrySet().stream()
                .filter(row -> knownSlugs.contains(row.getKey())).toList();
        if (counts.isEmpty()) {
            throw new IllegalStateException("시작 시점에 가져온 tripleS 기준 수량이 비어 있습니다.");
        }
        long cutoverBlock = quantity(call("eth_blockNumber", List.of()));
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO objekt_mint_cursor (chain_id, contract, next_block) VALUES (?, ?, ?)",
                    chainId, contract, cutoverBlock + 1);
            if (!missing.isEmpty()) repository.saveAll(missing);
            jdbc.batchUpdate("INSERT INTO objekt_mint_total (slug, baseline_count) VALUES (?, ?)",
                    counts, 250, (statement, row) -> {
                        statement.setString(1, row.getKey());
                        statement.setLong(2, row.getValue());
                    });
        });
        log.info("오브젝트 기준 수량 저장 완료: {}개 컬렉션, 체인 수집 시작 블록 {}", counts.size(), cutoverBlock + 1);
    }

    private Batch prepare(long start, long end) {
        Map<String, AbstractTransferLog> logs = new LinkedHashMap<>();
        for (AbstractTransferLog event : queryLogs(start, end, List.of(TRANSFER, topic(NULL)))) {
            logs.put(event.blockNumber() + ":" + event.logIndex(), event);
        }
        List<Object> recipients = new ArrayList<>();
        recipients.add(TRANSFER);
        recipients.add(null);
        recipients.add(List.of(topic(NULL), topic(spin)));
        for (AbstractTransferLog event : queryLogs(start, end, recipients)) {
            logs.put(event.blockNumber() + ":" + event.logIndex(), event);
        }
        List<Event> events = new ArrayList<>();
        Map<String, ObjektCatalog> newCollections = new LinkedHashMap<>();
        Map<String, String> tokenSlugs = new HashMap<>();
        Set<TokenEvent> seen = new HashSet<>();
        List<String> tokens = logs.values().stream()
                .map(row -> Long.toUnsignedString(row.tokenId())).distinct().toList();
        for (int offset = 0; offset < tokens.size(); offset += 250) {
            namedJdbc.query("""
                    SELECT token_id, event_kind, slug FROM objekt_mint_event
                    WHERE chain_id = :chain AND contract = :contract AND token_id IN (:tokens)
                    """, Map.of("chain", chainId, "contract", contract,
                    "tokens", tokens.subList(offset, Math.min(offset + 250, tokens.size()))), row -> {
                String token = row.getString("token_id");
                tokenSlugs.put(token, row.getString("slug"));
                seen.add(new TokenEvent(token, row.getString("event_kind")));
            });
        }
        Map<Long, Instant> blockDates = new HashMap<>();
        for (AbstractTransferLog row : logs.values().stream()
                .sorted(Comparator.comparingLong(AbstractTransferLog::blockNumber)
                        .thenComparingInt(AbstractTransferLog::logIndex)).toList()) {
            String token = Long.toUnsignedString(row.tokenId());
            String kind = eventKind(row.fromAddress(), row.toAddress(), spin);
            if (kind == null) throw new IllegalStateException("RPC가 필터 범위 밖의 이벤트를 반환했습니다.");
            if (!seen.add(new TokenEvent(token, kind))) continue;
            String slug = tokenSlugs.get(token);
            if (slug == null) {
                JsonNode metadata = market.fetchTokenMetadata(token);
                Map<String, String> traits = new HashMap<>();
                for (JsonNode attribute : metadata.path("attributes")) {
                    traits.put(attribute.path("trait_type").asText(), attribute.path("value").asText());
                }
                if (!"triples".equalsIgnoreCase(requiredTrait(traits, "Artist"))) {
                    tokenSlugs.put(token, "");
                    continue;
                }
                if ("Unit".equalsIgnoreCase(traits.get("Class"))) {
                    for (JsonNode attribute : metadata.path("attributes")) {
                        String value = attribute.path("value").asText();
                        if (value.toLowerCase(Locale.ROOT).contains(" x ")) traits.put("Member", value);
                    }
                }
                String collectionId = metadata.path("name").asText().replaceFirst(" #\\d+$", "");
                if (collectionId.isBlank()) throw new IllegalStateException("오브젝트 컬렉션 이름이 없습니다.");
                Map<String, Object> existing = catalog.findByKey(collectionId);
                boolean newCollection = existing == null || existing.isEmpty();
                slug = newCollection ? collectionSlug(collectionId) : existing.getOrDefault("slug", "").toString();
                if (slug.isBlank()) throw new IllegalStateException("저장된 컬렉션에 slug가 없습니다.");
                if (newCollection && !newCollections.containsKey(slug)) {
                    Instant date = blockDates.computeIfAbsent(row.blockNumber(), this::blockDate);
                    newCollections.put(slug, collection(metadata, traits, collectionId, slug, date));
                }
                tokenSlugs.put(token, slug);
            }
            if (!slug.isEmpty()) events.add(new Event(row, token, kind, slug));
        }
        return new Batch(events, List.copyOf(newCollections.values()));
    }

    static String eventKind(String from, String to, String spin) {
        if (NULL.equals(from)) return "mint";
        if (NULL.equals(to)) return "burn";
        if (spin.equals(to)) return "spin";
        return null;
    }

    private static String requiredTrait(Map<String, String> traits, String key) {
        String value = traits.getOrDefault(key, "").strip();
        if (value.isEmpty()) throw new IllegalStateException("COSMO 메타데이터에 " + key + " 항목이 없습니다.");
        return value;
    }

    private static String collectionSlug(String name) {
        String slug = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u036f]", "").replaceAll("[^\\w\\s-]", "").replaceAll("\\s+", "-");
        if (!slug.matches("[a-z0-9_][a-z0-9_-]{0,199}")) {
            throw new IllegalStateException("컬렉션 slug가 올바르지 않습니다.");
        }
        return slug;
    }

    private static ObjektCatalog collection(JsonNode metadata, Map<String, String> traits, String name, String slug,
                                            Instant date) {
        String number = requiredTrait(traits, "Collection");
        String image = metadata.path("image").asText();
        return new ObjektCatalogDto(slug, date.toString(), slug, name, requiredTrait(traits, "Season"),
                requiredTrait(traits, "Member"), "triples", number, requiredTrait(traits, "Class"),
                image.replaceFirst("/(4x|original)/", "/thumbnail/"), image, "",
                metadata.path("background_color").asText(), "", number.contains("Z") ? "online" : "offline",
                "", "", "", "", "").toEntity();
    }

    private long cursor(boolean lock) {
        return jdbc.query("SELECT next_block FROM objekt_mint_cursor WHERE chain_id = ? AND contract = ?"
                        + (lock ? " FOR UPDATE" : ""),
                rows -> rows.next() ? rows.getLong(1) : -1L, chainId, contract);
    }

    private Commit store(long expected, long end, Batch batch) {
        if (cursor(true) != expected) return null;
        if (!batch.collections().isEmpty()) repository.saveAll(batch.collections());
        Map<String, Long> increments = new LinkedHashMap<>();
        jdbc.batchUpdate("""
                INSERT INTO objekt_mint_event
                    (chain_id, contract, token_id, event_kind, slug, block_number, log_index, tx_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, batch.events(), 250, (statement, event) -> {
            statement.setString(1, chainId);
            statement.setString(2, contract);
            statement.setString(3, event.token());
            statement.setString(4, event.kind());
            statement.setString(5, event.slug());
            statement.setLong(6, event.log().blockNumber());
            statement.setInt(7, event.log().logIndex());
            statement.setString(8, event.log().txHash());
        });
        for (Event event : batch.events()) {
            if ("mint".equals(event.kind())) increments.merge(event.slug(), 1L, Long::sum);
        }
        jdbc.batchUpdate("""
                INSERT INTO objekt_mint_total (slug, baseline_count, new_mints) VALUES (?, 0, ?)
                ON DUPLICATE KEY UPDATE new_mints = new_mints + VALUES(new_mints)
                """, increments.entrySet(), 250, (statement, row) -> {
            statement.setString(1, row.getKey());
            statement.setLong(2, row.getValue());
        });
        jdbc.update("UPDATE objekt_mint_cursor SET next_block = ? WHERE chain_id = ? AND contract = ?",
                end + 1, chainId, contract);
        return new Commit(!batch.collections().isEmpty(), !increments.isEmpty());
    }

    private void publishCounts() {
        Map<String, Long> counts = jdbc.query("SELECT slug, baseline_count + new_mints FROM objekt_mint_total ORDER BY slug",
                rows -> {
                    Map<String, Long> result = new LinkedHashMap<>();
                    while (rows.next()) result.put(rows.getString(1), rows.getLong(2));
                    return result;
                });
        market.installMintCounts(counts);
    }

    private void reloadView() {
        catalog.reloadStoredCatalog();
        publishCounts();
        loaded = true;
    }

    private List<AbstractTransferLog> queryLogs(long start, long end, List<?> topics) {
        JsonNode response = call("eth_getLogs",
                List.of(Map.of("address", contract, "topics", topics, "fromBlock", hex(start), "toBlock", hex(end))));
        List<AbstractTransferLog> parsed = AbstractTransferLog.parse(response);
        if (!response.isArray() || response.size() != parsed.size()) {
            throw new IllegalStateException("RPC 이벤트 목록을 완전히 읽지 못했습니다.");
        }
        for (AbstractTransferLog row : parsed) {
            if (row.blockNumber() < start || row.blockNumber() > end || row.logIndex() < 0
                    || !row.txHash().matches("0x[0-9a-f]{64}")) {
                throw new IllegalStateException("RPC 이벤트 값이 올바르지 않습니다.");
            }
        }
        return parsed;
    }

    private Instant blockDate(long block) {
        return Instant.ofEpochSecond(quantity(call("eth_getBlockByNumber", List.of(hex(block), false)).path("timestamp")));
    }

    private JsonNode call(String method, List<?> parameters) {
        JsonNode response = rpc.post().body(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", parameters))
                .retrieve().body(JsonNode.class);
        if (response == null || response.has("error") || !response.hasNonNull("result")) {
            throw new IllegalStateException(method + " 응답을 읽지 못했습니다.");
        }
        return response.get("result");
    }

    private static long quantity(JsonNode value) {
        String hex = value.asText();
        if (!hex.matches("0x[0-9a-fA-F]+")) throw new IllegalStateException("RPC 블록 번호를 읽지 못했습니다.");
        return Long.parseLong(hex.substring(2), 16);
    }

    private static String topic(String address) {
        if (!address.matches("0x[0-9a-f]{40}")) throw new IllegalStateException("체인 주소 설정이 올바르지 않습니다.");
        return "0x" + "0".repeat(24) + address.substring(2);
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }

    private record Event(AbstractTransferLog log, String token, String kind, String slug) {}
    private record TokenEvent(String token, String kind) {}
    private record Batch(List<Event> events, List<ObjektCatalog> collections) {}
    private record Commit(boolean newCollections, boolean newMints) {}
}

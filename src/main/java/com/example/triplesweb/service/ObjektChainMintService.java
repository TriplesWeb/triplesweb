package com.example.triplesweb.service;

import com.example.triplesweb.dto.ObjektCatalogDto;
import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.infra.GzipRestClients;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
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

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Indexes issuance and retirement events without storing transfers between collectors. */
@Service
@ConditionalOnProperty(name = "objekt.mints.chain-enabled", havingValue = "true")
public class ObjektChainMintService {
    private static final Logger log = LoggerFactory.getLogger(ObjektChainMintService.class);
    private static final String NULL = "0x" + "0".repeat(40);
    private static final String TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final Batch EMPTY_BATCH = new Batch(List.of(), List.of());
    private static final int PREVIEW_METADATA_LIMIT = 32;
    private static final int PREVIEW_CACHE_LIMIT = 2_000;
    private static final int METADATA_ATTEMPTS = 3;
    private static final int CONFIRMED_METADATA_LIMIT = 5_000;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate transactions;
    private final ObjektService catalog;
    private final ObjektMarketDataService market;
    private final RestClient rpc;
    private final String contract;
    private final String spin;
    private final String chainId;
    private final int confirmations;
    private final int blockBatch;

    private final ExecutorService metadataWorkers = Executors.newFixedThreadPool(8);
    private final Map<AbstractTransferLog, TokenMetadata> previewMetadata = new LinkedHashMap<>();
    private final Map<String, CachedMetadata> confirmedMetadata = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<AbstractTransferLog, Long> metadataRetryAt = new LinkedHashMap<>();
    private Batch pending = EMPTY_BATCH;
    private long nextConfirmedBlock = -1;
    private Map<String, Long> confirmedCounts = Map.of();
    private Map<String, Long> displayedCounts = Map.of();
    private boolean loaded;
    private boolean chainVerified;
    private long failedStart = -1;
    private long failedEnd = -1;
    private long consecutiveFailures;

    public ObjektChainMintService(JdbcTemplate jdbc, TransactionTemplate transactions, ObjektService catalog,
                                  ObjektMarketDataService market,
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
        long head;
        try {
            if (!chainVerified) {
                if (!chainId.equals(Long.toString(quantity(call("eth_chainId", List.of()))))) {
                    throw new IllegalStateException("RPC 체인이 설정과 일치하지 않습니다.");
                }
                chainVerified = true;
            }
            long next = cursor(false);
            if (next < 0) {
                initialize();
                reloadView(cursor(false));
                return;
            }
            if (!loaded || next != nextConfirmedBlock) reloadView(next);
            head = quantity(call("eth_blockNumber", List.of()));
        } catch (RuntimeException error) {
            log.warn("오브젝트 민팅 수집 재시도: {}", error.getMessage());
            return;
        }

        try {
            collectPreview(head);
        } catch (RuntimeException error) {
            log.warn("오브젝트 최근 민팅 조회 재시도: {}", error.getMessage());
        }
        collectConfirmed(head - confirmations);
    }

    private void collectConfirmed(long confirmed) {
        long next = nextConfirmedBlock;
        if (next > confirmed) return;
        long end = Math.min(next + blockBatch - 1, confirmed);
        Batch batch;
        Commit result;
        try {
            batch = prepare(readEvents(next, end, false), false);
            result = transactions.execute(status -> store(next, end, batch));
        } catch (RuntimeException error) {
            warnConfirmedFailure(next, end, error);
            return;
        }
        consecutiveFailures = 0;
        try {
            applyConfirmed(end, batch, result);
        } catch (RuntimeException error) {
            log.warn("오브젝트 확정 민팅 조회 캐시 갱신 실패: {}", error.getMessage());
        }
    }

    private void warnConfirmedFailure(long start, long end, RuntimeException error) {
        if (start != failedStart || end != failedEnd) consecutiveFailures = 0;
        failedStart = start;
        failedEnd = end;
        consecutiveFailures++;
        Throwable root = error;
        SQLException sqlError = null;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            root = cause;
            if (cause instanceof SQLException sql) sqlError = sql;
        }
        String reason = error instanceof CompletionException
                ? error.getCause().getMessage() + " (" + root + ")" : root.toString();
        log.warn("오브젝트 민팅 수집 재시도 (블록 {}~{}, 연속 실패 {}회, SQL 오류 코드 {}): {}",
                start, end, consecutiveFailures, sqlError == null ? "-" : sqlError.getErrorCode(), reason);
    }

    private void applyConfirmed(long end, Batch batch, Commit result) {
        if (result == null) {
            loaded = false;
            return;
        }

        loaded = false;
        updateConfirmedCursor(end + 1);
        if (result.newMints()) {
            confirmedCounts = loadConfirmedCounts();
            Set<String> confirmedTokens = new HashSet<>();
            for (Event event : batch.events()) {
                if ("mint".equals(event.kind())) confirmedTokens.add(event.token());
            }
            pending = new Batch(pending.events().stream()
                    .filter(event -> !confirmedTokens.contains(event.token())).toList(), pending.collections());
        }
        publishCounts();
        if (result.reloadCatalog()) catalog.reloadStoredCatalog();
        publishPendingCollections();
        loaded = true;
    }

    private void collectPreview(long head) {
        // Bound the live scan even if the persistent cursor is catching up after downtime.
        long start = Math.max(nextConfirmedBlock, head - Math.max(blockBatch, confirmations) + 1);
        try {
            if (start > head) {
                pending = EMPTY_BATCH;
            } else {
                String tipHash = blockHash(head);
                var logs = readEvents(start, head, true);
                Batch preview = prepare(logs, true);
                if (!tipHash.equals(blockHash(head))) {
                    throw new IllegalStateException("최근 블록이 조회 중 교체되었습니다.");
                }
                pending = preview;
            }
            publishCounts();
            publishPendingCollections();
        } finally {
            long keepFrom = Math.min(start, head) - confirmations;
            previewMetadata.keySet().removeIf(event -> event.blockNumber() < keepFrom);
            metadataRetryAt.keySet().removeIf(event -> event.blockNumber() < keepFrom);
        }
    }

    @PreDestroy
    void stopMetadataWorkers() {
        metadataWorkers.shutdownNow();
    }

    @Scheduled(cron = "${objekt.mints.audit-cron:0 40 4 * * *}", zone = "Asia/Seoul",
            scheduler = "objektMintScheduler")
    void audit() {
        if (!loaded) return;
        try {
            List<ObjektCatalog> externalCatalog = catalog.fetchExternalCatalog();
            catalog.enrichChainBackImages(externalCatalog);
            Map<String, Long> external = market.fetchExternalMintCounts();
            Set<String> ambiguousSlugs = ambiguousExternalSlugs(externalCatalog);
            int missing = 0;
            int different = 0;
            List<String> examples = new ArrayList<>();
            for (ObjektCatalog row : externalCatalog) {
                Map<String, Object> existing = findStoredCollection(row.getCollectionId());
                boolean missingCollection = existing == null;
                Long local = missingCollection ? null : confirmedCounts.get(storedSlug(existing));
                Long observed = ambiguousSlugs.contains(row.getSlug()) ? null : external.get(row.getSlug());
                if (missingCollection) missing++;
                if (observed != null && !observed.equals(local)) different++;
                if (examples.size() < 10 && (missingCollection || observed != null && !observed.equals(local))) {
                    examples.add(row.getCollectionId() + " (집계 " + local + ", 외부 공급량 " + observed + ")");
                }
            }
            log.info("오브젝트 일일 대조: 카탈로그 누락 {}, 수량 차이 {}, 공급량 slug 모호 {}, 예시 {}, 모호 slug 예시 {}",
                    missing, different, ambiguousSlugs.size(), examples, ambiguousSlugs.stream().limit(10).toList());
        } catch (RuntimeException error) {
            log.warn("오브젝트 일일 대조 실패: {}", error.getMessage());
        }
    }

    private void initialize() {
        List<ObjektCatalog> externalCatalog = catalog.fetchExternalCatalog();
        Map<String, Long> externalCounts = market.fetchExternalMintCounts();
        Set<String> ambiguousSlugs = ambiguousExternalSlugs(externalCatalog);
        if (!ambiguousSlugs.isEmpty()) {
            log.warn("오브젝트 초기 기준 수량에서 모호한 외부 slug {}개 제외: {}",
                    ambiguousSlugs.size(), ambiguousSlugs.stream().limit(10).toList());
        }
        long cutoverBlock = quantity(call("eth_blockNumber", List.of()));
        int seeded = transactions.execute(status -> {
            jdbc.update("INSERT INTO objekt_mint_cursor (chain_id, contract, next_block) VALUES (?, ?, ?)",
                    chainId, contract, cutoverBlock + 1);
            Map<String, String> localSlugs = new HashMap<>();
            Set<String> occupiedSlugs = new HashSet<>();
            jdbc.query("SELECT collection_id, slug FROM objekt_catalog ORDER BY id", row -> {
                String slug = row.getString("slug");
                if (slug == null || slug.isBlank()) return;
                localSlugs.putIfAbsent(ObjektService.collectionNameKey(row.getString("collection_id")), slug);
                occupiedSlugs.add(slug.stripTrailing().toLowerCase(Locale.ROOT));
            });
            Map<String, Long> counts = new LinkedHashMap<>();
            for (ObjektCatalog row : externalCatalog) {
                String sourceSlug = row.getSlug();
                if (sourceSlug == null || sourceSlug.isBlank()) continue;
                String name = row.getCollectionId();
                String localSlug = localSlugs.computeIfAbsent(ObjektService.collectionNameKey(name), key -> {
                    String slug = sourceSlug;
                    if (!occupiedSlugs.add(slug.stripTrailing().toLowerCase(Locale.ROOT))) {
                        slug = disambiguatedSlug(name);
                        if (!occupiedSlugs.add(slug)) {
                            throw new IllegalStateException("초기 카탈로그 slug 충돌: " + slug);
                        }
                    }
                    return storeCollection(row, slug);
                });
                Long count = ambiguousSlugs.contains(sourceSlug) ? null : externalCounts.get(sourceSlug);
                if (count != null) counts.putIfAbsent(localSlug, count);
            }
            if (counts.isEmpty()) {
                throw new IllegalStateException("시작 시점에 가져온 tripleS 기준 수량이 비어 있습니다.");
            }
            jdbc.batchUpdate("""
                    INSERT INTO objekt_mint_total (slug, baseline_count) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE baseline_count = baseline_count
                    """, counts.entrySet(), 250, (statement, row) -> {
                        statement.setString(1, row.getKey());
                        statement.setLong(2, row.getValue());
                    });
            return counts.size();
        });
        log.info("오브젝트 기준 수량 저장 완료: {}개 컬렉션, 체인 수집 시작 블록 {}", seeded, cutoverBlock + 1);
    }

    private static Set<String> ambiguousExternalSlugs(List<ObjektCatalog> externalCatalog) {
        Map<String, String> namesBySlug = new HashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (ObjektCatalog row : externalCatalog) {
            String slug = row.getSlug();
            if (slug == null || slug.isBlank()) continue;
            String name = ObjektService.collectionNameKey(row.getCollectionId());
            String first = namesBySlug.putIfAbsent(slug, name);
            if (first != null && !first.equals(name)) ambiguous.add(slug);
        }
        return ambiguous;
    }

    private List<AbstractTransferLog> readEvents(long start, long end, boolean mintOnly) {
        Map<String, AbstractTransferLog> logs = new LinkedHashMap<>();
        for (AbstractTransferLog event : queryLogs(start, end, List.of(TRANSFER, topic(NULL)))) {
            logs.put(event.blockNumber() + ":" + event.logIndex(), event);
        }
        if (mintOnly) {
            if (logs.values().stream().anyMatch(row -> !row.blockHash().matches("0x[0-9a-f]{64}"))) {
                throw new IllegalStateException("최근 민팅 이벤트의 블록 해시를 읽지 못했습니다.");
            }
            return List.copyOf(logs.values());
        }
        List<Object> recipients = new ArrayList<>();
        recipients.add(TRANSFER);
        recipients.add(null);
        recipients.add(List.of(topic(NULL), topic(spin)));
        for (AbstractTransferLog event : queryLogs(start, end, recipients)) {
            logs.put(event.blockNumber() + ":" + event.logIndex(), event);
        }
        return List.copyOf(logs.values());
    }

    private Batch prepare(List<AbstractTransferLog> logs, boolean preview) {
        List<Event> events = new ArrayList<>();
        Map<String, ObjektCatalog> newCollections = new LinkedHashMap<>();
        Set<String> candidateSlugs = new HashSet<>();
        Map<String, CollectionRef> tokenCollections = new HashMap<>();
        Set<TokenEvent> seen = new HashSet<>();
        List<String> tokens = logs.stream()
                .map(row -> Long.toUnsignedString(row.tokenId())).distinct().toList();
        for (int offset = 0; offset < tokens.size(); offset += 250) {
            namedJdbc.query("""
                    SELECT token_id, event_kind, slug FROM objekt_mint_event
                    WHERE chain_id = :chain AND contract = :contract AND token_id IN (:tokens)
                    """, Map.of("chain", chainId, "contract", contract,
                    "tokens", tokens.subList(offset, Math.min(offset + 250, tokens.size()))), row -> {
                String token = row.getString("token_id");
                tokenCollections.put(token, new CollectionRef(row.getString("slug"), null));
                seen.add(new TokenEvent(token, row.getString("event_kind")));
            });
        }
        List<AbstractTransferLog> ordered = logs.stream()
                .sorted(Comparator.comparingLong(AbstractTransferLog::blockNumber)
                        .thenComparingInt(AbstractTransferLog::logIndex)).toList();
        if (preview) prefetchPreviewMetadata(ordered, seen);
        Map<String, TokenMetadata> batchMetadata = preview
                ? Map.of() : prefetchConfirmedMetadata(ordered, tokenCollections.keySet());

        Map<Long, Instant> blockDates = new HashMap<>();
        for (AbstractTransferLog row : ordered) {
            String token = Long.toUnsignedString(row.tokenId());
            String kind = eventKind(row.fromAddress(), row.toAddress(), spin);
            if (kind == null || (preview && !"mint".equals(kind))) {
                throw new IllegalStateException("RPC가 필터 범위 밖의 이벤트를 반환했습니다.");
            }
            if (!seen.add(new TokenEvent(token, kind))) continue;
            CollectionRef ref = tokenCollections.get(token);
            if (ref == null) {
                TokenMetadata metadata = preview ? previewMetadata.get(row)
                        : Objects.requireNonNull(batchMetadata.get(token), "토큰 " + token + "의 확정 메타데이터가 없습니다.");
                if (metadata == null) continue;
                ref = resolveCollection(metadata, row, newCollections, candidateSlugs, blockDates);
                tokenCollections.put(token, ref);
            }
            if (!ref.slug().isEmpty()) events.add(new Event(row, token, kind, ref));
        }
        return new Batch(events, List.copyOf(newCollections.values()));
    }

    private CollectionRef resolveCollection(TokenMetadata metadata, AbstractTransferLog row,
                                            Map<String, ObjektCatalog> newCollections, Set<String> candidateSlugs,
                                            Map<Long, Instant> blockDates) {
        String name = metadata.collectionId();
        if (name.isEmpty()) return new CollectionRef("", null);
        Map<String, Object> existing = findStoredCollection(name);
        if (existing != null) {
            return new CollectionRef(storedSlug(existing), null);
        }
        ObjektCatalog candidate = newCollections.computeIfAbsent(ObjektService.collectionNameKey(name), key -> {
            Instant date = row.blockTimestamp() == null
                    ? blockDates.computeIfAbsent(row.blockNumber(), this::blockDate)
                    : Instant.ofEpochSecond(row.blockTimestamp());
            String slug = collectionSlug(name);
            Map<String, Object> occupying = catalog.findByKey(slug);
            if ((occupying != null && occupying.get("collectionId") instanceof String otherName
                    && !key.equals(ObjektService.collectionNameKey(otherName))) || candidateSlugs.contains(slug)) {
                slug = disambiguatedSlug(name);
            }
            if (!candidateSlugs.add(slug)) {
                throw new IllegalStateException("묶음 내 컬렉션 slug 충돌: " + slug);
            }
            return collection(metadata.data(), metadata.traits(), name, slug, date);
        });
        return new CollectionRef(candidate.getSlug(), candidate.getCollectionId());
    }

    private Map<String, Object> findStoredCollection(String name) {
        Map<String, Object> existing = catalog.findStoredByCollectionName(name);
        return existing == null || existing.isEmpty() ? null : existing;
    }

    private Map<String, TokenMetadata> prefetchConfirmedMetadata(
            List<AbstractTransferLog> logs, Set<String> storedTokens) {
        Map<String, TokenMetadata> result = new HashMap<>();
        Map<String, MetadataRequest> requests = new LinkedHashMap<>();
        for (AbstractTransferLog row : logs) {
            String token = Long.toUnsignedString(row.tokenId());
            if (storedTokens.contains(token) || result.containsKey(token) || requests.containsKey(token)) continue;
            CachedMetadata cached = confirmedMetadata.get(token);
            if (cached != null && cached.source().equals(row)) {
                result.put(token, cached.metadata());
                continue;
            }
            TokenMetadata preview = previewMetadata.get(row);
            if (preview != null) {
                rememberConfirmedMetadata(token, row, preview);
                result.put(token, preview);
                continue;
            }
            requests.put(token, new MetadataRequest(row,
                    CompletableFuture.supplyAsync(() -> fetchConfirmedMetadata(token), metadataWorkers)));
        }

        // Keep every successful response, including those completed alongside a failed request.
        CompletionException failure = null;
        for (var entry : requests.entrySet()) {
            MetadataRequest request = entry.getValue();
            try {
                TokenMetadata metadata = request.response().join();
                rememberConfirmedMetadata(entry.getKey(), request.source(), metadata);
                result.put(entry.getKey(), metadata);
            } catch (CompletionException error) {
                if (failure == null) failure = error;
            }
        }
        if (failure != null) throw failure;
        return result;
    }

    private void rememberConfirmedMetadata(String token, AbstractTransferLog row, TokenMetadata metadata) {
        confirmedMetadata.put(token, new CachedMetadata(row, metadata));
        if (confirmedMetadata.size() > CONFIRMED_METADATA_LIMIT) {
            confirmedMetadata.remove(confirmedMetadata.keySet().iterator().next());
        }
    }

    private TokenMetadata fetchConfirmedMetadata(String token) {
        for (int attempt = 1; ; attempt++) {
            try {
                return fetchMetadata(token);
            } catch (RuntimeException error) {
                if (attempt == METADATA_ATTEMPTS) {
                    throw new IllegalStateException("토큰 " + token + "의 메타데이터 조회 실패", error);
                }
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("확정 민팅 메타데이터 조회가 중단되었습니다.", interrupted);
                }
            }
        }
    }

    private void prefetchPreviewMetadata(List<AbstractTransferLog> logs, Set<TokenEvent> seen) {
        long now = System.currentTimeMillis();
        Map<AbstractTransferLog, CompletableFuture<TokenMetadata>> requests = new LinkedHashMap<>();
        for (AbstractTransferLog row : logs) {
            if (requests.size() == PREVIEW_METADATA_LIMIT) break;
            String token = Long.toUnsignedString(row.tokenId());
            if (seen.contains(new TokenEvent(token, "mint")) || previewMetadata.containsKey(row)
                    || metadataRetryAt.getOrDefault(row, 0L) > now) continue;
            requests.put(row, CompletableFuture.supplyAsync(() -> fetchMetadata(token), metadataWorkers));
        }
        requests.forEach((row, request) -> {
            try {
                putPreviewEntry(previewMetadata, row, request.join());
                metadataRetryAt.remove(row);
            } catch (RuntimeException error) {
                // A token whose metadata is still propagating should not hide the other new cards.
                putPreviewEntry(metadataRetryAt, row, System.currentTimeMillis() + 20_000);
            }
        });
    }

    private static <T> void putPreviewEntry(Map<AbstractTransferLog, T> cache, AbstractTransferLog row, T value) {
        cache.put(row, value);
        if (cache.size() > PREVIEW_CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
    }

    private TokenMetadata fetchMetadata(String token) {
        JsonNode metadata = market.fetchTokenMetadata(token);
        Map<String, String> traits = new HashMap<>();
        for (JsonNode attribute : metadata.path("attributes")) {
            traits.put(attribute.path("trait_type").asText(), attribute.path("value").asText());
        }
        if (!"triples".equalsIgnoreCase(requiredTrait(traits, "Artist"))) {
            return new TokenMetadata(metadata, traits, "");
        }
        if ("Unit".equalsIgnoreCase(traits.get("Class"))) {
            for (JsonNode attribute : metadata.path("attributes")) {
                String value = attribute.path("value").asText();
                if (value.toLowerCase(Locale.ROOT).contains(" x ")) traits.put("Member", value);
            }
        }
        String name = ObjektService.normalizeCollectionName(metadata.path("name").asText())
                .replaceFirst(" #\\d+$", "");
        if (name.isBlank()) throw new IllegalStateException("오브젝트 컬렉션 이름이 없습니다.");
        if (findStoredCollection(name) == null) {
            collectionSlug(name);
            for (String trait : List.of("Season", "Collection", "Member", "Class")) requiredTrait(traits, trait);
        }
        return new TokenMetadata(metadata, traits, name);
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

    private static String disambiguatedSlug(String name) {
        String base = collectionSlug(name);
        String suffix = UUID.nameUUIDFromBytes(ObjektService.collectionNameKey(name).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        // Chain-created rows also use the slug as their external ID (VARCHAR(80)).
        return base.substring(0, Math.min(base.length(), 79 - suffix.length())) + "-" + suffix;
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
        Long nextBlock = jdbc.query("SELECT next_block FROM objekt_mint_cursor WHERE chain_id = ? AND contract = ?"
                        + (lock ? " FOR UPDATE" : ""),
                rows -> rows.next() ? rows.getLong(1) : null, chainId, contract);
        return nextBlock == null ? -1L : nextBlock;
    }

    private Commit store(long expected, long end, Batch batch) {
        if (cursor(true) != expected) return null;
        Map<String, String> storedSlugs = new HashMap<>();
        for (ObjektCatalog collection : batch.collections()) {
            storedSlugs.put(collection.getCollectionId(), storeCollection(collection));
        }
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
            statement.setString(5, event.storedSlug(storedSlugs));
            statement.setLong(6, event.log().blockNumber());
            statement.setInt(7, event.log().logIndex());
            statement.setString(8, event.log().txHash());
        });
        for (Event event : batch.events()) {
            if ("mint".equals(event.kind())) increments.merge(event.storedSlug(storedSlugs), 1L, Long::sum);
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
        return new Commit(!storedSlugs.isEmpty(), !increments.isEmpty());
    }

    private String storeCollection(ObjektCatalog row) {
        String name = row.getCollectionId();
        String slug = row.getSlug();
        if (slugOccupied(slug, name)) {
            slug = disambiguatedSlug(name);
            if (slugOccupied(slug, name)) throw new IllegalStateException("저장된 컬렉션과 slug 충돌: " + slug);
        }
        return storeCollection(row, slug);
    }

    private String storeCollection(ObjektCatalog row, String slug) {
        String name = row.getCollectionId();
        boolean slugChanged = !slug.equals(row.getSlug());
        String externalId = Objects.equals(row.getExternalId(), row.getSlug()) ? slug : row.getExternalId();
        String searchText = slugChanged ? row.getSearchText() + " " + slug : row.getSearchText();
        // The unique index decides whether the name is already present under the database collation.
        jdbc.update("""
                INSERT INTO objekt_catalog
                    (external_id, created_at, slug, collection_id, season, member, artist, collection_no,
                     objekt_class, thumbnail_image, front_image, back_image, background_color, text_color,
                     on_offline, has_audio, original_front_image, original_back_image, front_media, band_image_url,
                     search_text, season_key, class_key, number_value)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, externalId, row.getCreatedAt(), slug, name, row.getSeason(),
                row.getMember(), row.getArtist(), row.getCollectionNo(), row.getObjektClass(), row.getThumbnailImage(),
                row.getFrontImage(), row.getBackImage(), row.getBackgroundColor(), row.getTextColor(), row.getOnOffline(),
                row.getHasAudio(), row.getOriginalFrontImage(), row.getOriginalBackImage(), row.getFrontMedia(),
                row.getBandImageUrl(), searchText, row.getSeasonKey(), row.getClassKey(), row.getNumberValue());
        StoredCollection stored = jdbc.queryForObject(
                "SELECT collection_id, slug FROM objekt_catalog WHERE collection_id = ? FOR UPDATE",
                (result, index) -> new StoredCollection(result.getString("collection_id"), result.getString("slug")),
                name);
        if (!ObjektService.collectionNameKey(name).equals(ObjektService.collectionNameKey(stored.name()))) {
            throw new IllegalStateException("DB 컬렉션 이름 불일치: 요청 '" + name + "', 저장 '" + stored.name() + "'");
        }
        requireStoredSlug(stored.slug());
        return stored.slug();
    }

    private boolean slugOccupied(String slug, String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM objekt_catalog WHERE slug = ? AND collection_id <> ?)
                """, Boolean.class, slug, name));
    }

    private static void requireStoredSlug(String slug) {
        if (slug == null || slug.isBlank()) throw new IllegalStateException("저장된 컬렉션에 slug가 없습니다.");
    }

    private static String storedSlug(Map<String, Object> collection) {
        String slug = (String) collection.get("slug");
        requireStoredSlug(slug);
        return slug;
    }

    private Map<String, Long> loadConfirmedCounts() {
        return jdbc.query("SELECT slug, baseline_count + new_mints FROM objekt_mint_total ORDER BY slug",
                rows -> {
                    Map<String, Long> result = new TreeMap<>();
                    while (rows.next()) result.put(rows.getString(1), rows.getLong(2));
                    return result;
                });
    }

    private void publishCounts() {
        Map<String, Long> counts = new TreeMap<>(confirmedCounts);
        for (Event event : pending.events()) {
            if (event.log().blockNumber() >= nextConfirmedBlock) counts.merge(event.slug(), 1L, Long::sum);
        }
        if (counts.equals(displayedCounts)) return;
        market.installMintCounts(counts);
        displayedCounts = counts;
    }

    private void publishPendingCollections() {
        Set<String> pendingSlugs = new HashSet<>();
        for (Event event : pending.events()) {
            if (event.log().blockNumber() >= nextConfirmedBlock) pendingSlugs.add(event.slug());
        }
        catalog.publishPendingCollections(pending.collections().stream()
                .filter(row -> pendingSlugs.contains(row.getSlug())).toList());
    }

    private void reloadView(long next) {
        updateConfirmedCursor(next);
        confirmedCounts = loadConfirmedCounts();
        publishCounts();
        catalog.reloadStoredCatalog();
        publishPendingCollections();
        loaded = true;
    }

    private void updateConfirmedCursor(long next) {
        nextConfirmedBlock = next;
        confirmedMetadata.values().removeIf(entry -> entry.source().blockNumber() < next);
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

    private String blockHash(long block) {
        String hash = call("eth_getBlockByNumber", List.of(hex(block), false)).path("hash").asText();
        if (!hash.matches("0x[0-9a-fA-F]{64}")) throw new IllegalStateException("RPC 블록 해시를 읽지 못했습니다.");
        return hash;
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

    private record TokenMetadata(JsonNode data, Map<String, String> traits, String collectionId) {}
    private record StoredCollection(String name, String slug) {}
    private record CachedMetadata(AbstractTransferLog source, TokenMetadata metadata) {}
    private record MetadataRequest(AbstractTransferLog source, CompletableFuture<TokenMetadata> response) {}
    private record CollectionRef(String slug, String candidateId) {}
    private record Event(AbstractTransferLog log, String token, String kind, CollectionRef collection) {
        String slug() {
            return collection.slug();
        }

        String storedSlug(Map<String, String> storedSlugs) {
            return collection.candidateId() == null ? slug() : storedSlugs.get(collection.candidateId());
        }
    }
    private record TokenEvent(String token, String kind) {}
    private record Batch(List<Event> events, List<ObjektCatalog> collections) {}
    private record Commit(boolean reloadCatalog, boolean newMints) {}
}

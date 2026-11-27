package com.example.triplesweb.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.triplesweb.entity.ObjektCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ObjektChainMintServiceTest {
    private static final String ZERO = "0".repeat(40);
    private static final String SPIN = "a".repeat(40);
    private static final String OWNER = "b".repeat(40);
    private static final String CONTRACT = "0x" + "c".repeat(40);
    private static final String TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @Test
    void previewsMintsRollsBackReorgsAndConfirmsWithoutCountingTwice(CapturedOutput output) throws Exception {
        var mapper = new ObjectMapper();
        var head = new AtomicLong(1000);
        var fork = new AtomicInteger();
        var server = rpcServer(mapper, request -> {
            var filter = request.path("params").path(0);
            return switch (request.path("method").asText()) {
                case "eth_chainId" -> "0xab5";
                case "eth_blockNumber" -> hex(head.get());
                case "eth_getBlockByNumber" -> Map.of("timestamp", "0x654a2abd", "hash", blockHash(fork.get()));
                case "eth_getLogs" -> {
                    boolean mintFilter = filter.path("topics").path(1).isTextual();
                    long start = Long.decode(filter.path("fromBlock").asText());
                    long end = Long.decode(filter.path("toBlock").asText());
                    if (start <= 1001 && end >= 1001 && fork.get() != 1) {
                        yield mintFilter
                                ? List.of(log(1001, 0, 2, ZERO, OWNER, fork.get()),
                                          log(1001, 1, 1, ZERO, OWNER, fork.get()))
                                : List.of(log(1001, 2, 1, OWNER, SPIN, fork.get()),
                                          log(1001, 3, 2, OWNER, ZERO, fork.get()));
                    }
                    yield mintFilter && start <= 1002 && end >= 1002 && head.get() >= 1062
                            ? List.of(log(1002, 0, 1, ZERO, OWNER, fork.get()),
                                      log(1002, 1, 3, ZERO, OWNER, fork.get()),
                                      log(1002, 2, 4, ZERO, OWNER, fork.get()),
                                      log(1002, 3, 5, ZERO, OWNER, fork.get()),
                                      log(1002, 4, 6, ZERO, OWNER, fork.get()),
                                      log(1002, 5, 7, ZERO, OWNER, fork.get())) : List.of();
                }
                default -> throw new AssertionError(request);
            };
        });

        try {
            var source = database(false);
            var jdbc = new JdbcTemplate(source);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            var catalog = mock(ObjektService.class);
            var market = mock(ObjektMarketDataService.class);
            Map<String, Object> existing = Map.of("slug", "atom01-member-101z",
                    "collectionId", "Atom01 Member 101Z");
            var external = new ObjektCatalog();
            external.setSlug("atom01-member-101z");
            external.setCollectionId("Atom01 Member 101Z");
            when(catalog.findStoredByKey("atom01-member-101z")).thenReturn(existing);
            when(catalog.findStoredByCollectionName("Atom01 Member 101Z")).thenReturn(existing);
            when(catalog.fetchExternalCatalog()).thenReturn(List.of(external));
            when(market.fetchExternalMintCounts()).thenReturn(Map.of("atom01-member-101z", 7L, "other-artist", 200L));
            when(market.fetchTokenMetadata("1")).thenReturn(mapper.readTree(metadata("101Z", "1")));
            when(market.fetchTokenMetadata("2")).thenReturn(mapper.readTree(metadata("102Z", "2")));
            when(market.fetchTokenMetadata("3")).thenReturn(mapper.readTree(metadata("103Z", "3")));
            when(market.fetchTokenMetadata("4")).thenReturn(mapper.readTree(metadata("103Z", "4")
                    .replace("Atom01 Member 103Z #4", "  aToM01  MEMBER  103z  #4  ")));
            when(market.fetchTokenMetadata("5")).thenReturn(mapper.readTree(metadata("104Z", "5")));
            when(market.fetchTokenMetadata("6")).thenReturn(mapper.readTree(metadata("101Z", "6").replace(" Member ", " Mem+ber ")));
            when(market.fetchTokenMetadata("7")).thenReturn(mapper.readTree(metadata("103Z", "7").replace(" Member ", " Mem+ber ")));
            String rpc = "http://127.0.0.1:" + server.getAddress().getPort();
            jdbc.update("INSERT INTO objekt_catalog (collection_id, slug) VALUES (?, ?)",
                    "Atom01 Member 101Z", "atom01-member-101z");
            var collector = new ObjektChainMintService(jdbc, transaction, catalog, market,
                    rpc, "2741", CONTRACT, "0x" + SPIN, 60, 200);
            collector.collect();
            verify(market).installMintCounts(Map.of("atom01-member-101z", 7L));
            try {
                head.set(1001);
                collector.collect();
                verify(market).installMintCounts(Map.of("atom01-member-101z", 8L, "atom01-member-102z", 1L));
                verify(catalog).publishPendingCollections(argThat(rows -> rows.size() == 1
                        && "atom01-member-102z".equals(rows.get(0).getSlug())));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_catalog", Long.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_event", Long.class)).isZero();

                fork.set(1);
                collector.collect();
                verify(market, times(2)).installMintCounts(Map.of("atom01-member-101z", 7L));
                verify(catalog, times(2)).publishPendingCollections(List.of());

                fork.set(2);
                when(market.fetchTokenMetadata("2")).thenThrow(new IllegalStateException("not ready"));
                collector.collect();
                verify(market).installMintCounts(Map.of("atom01-member-101z", 8L));
                assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1001);

                // Move the batch outside preview and change its log identity so both tokens need confirmed requests.
                fork.set(3);
                head.set(1202);
                collector.collect();
                verify(market, times(3)).fetchTokenMetadata("1");
                collector.collect();
                verify(market, times(3)).fetchTokenMetadata("1");
                verify(market, times(8)).fetchTokenMetadata("2");
                assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1001);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_event", Long.class)).isZero();
                assertThat(output).contains("블록 1001~1142, 연속 실패 2회", "토큰 2의 메타데이터 조회 실패",
                        "IllegalStateException: not ready");

                // A replacement block invalidates the formerly successful lookup.
                fork.set(4);
                collector.collect();
                verify(market, times(4)).fetchTokenMetadata("1");

                doReturn(mapper.readTree(metadata("102Z", "2"))).when(market).fetchTokenMetadata("2");
                fork.set(5);
                head.set(1060);
                collector.collect();
                verify(market, times(2)).installMintCounts(Map.of("atom01-member-101z", 8L, "atom01-member-102z", 1L));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_event", Long.class)).isZero();

                fork.set(6);
                var retryAttempts = new AtomicInteger();
                var tokenTwo = mapper.readTree(metadata("102Z", "2"));
                doReturn(tokenTwo).when(market).fetchTokenMetadata("2");
                when(market.fetchTokenMetadata("2")).thenAnswer(invocation -> {
                    if (retryAttempts.incrementAndGet() <= 3) throw new IllegalStateException("not ready yet");
                    return tokenTwo;
                });
                head.set(1061);
                collector.collect();
                // One preview request, then three confirmed requests including the final success.
                assertThat(retryAttempts).hasValue(4);
            } finally {
                collector.stopMetadataWorkers();
            }
            assertThat(jdbc.queryForList("SELECT event_kind FROM objekt_mint_event", String.class))
                    .containsExactlyInAnyOrder("mint", "mint", "spin", "burn");
            assertThat(jdbc.queryForList("SELECT slug FROM objekt_catalog", String.class))
                    .containsExactlyInAnyOrder("atom01-member-101z", "atom01-member-102z");
            verify(market, times(3)).installMintCounts(Map.of("atom01-member-101z", 8L, "atom01-member-102z", 1L));
            verify(market, never()).installMintCounts(argThat(counts ->
                    counts.getOrDefault("atom01-member-101z", 0L) > 8
                            || counts.getOrDefault("atom01-member-102z", 0L) > 1));

            when(market.fetchExternalMintCounts()).thenReturn(Map.of("atom01-member-101z", 4L));
            collector.audit();
            verify(catalog).enrichChainBackImages(List.of(external));
            assertThat(jdbc.queryForObject("SELECT baseline_count FROM objekt_mint_total WHERE slug = 'atom01-member-101z'",
                    Long.class)).isEqualTo(7);

            head.set(1062);
            jdbc.update("INSERT INTO objekt_catalog (collection_id, slug) VALUES (?, ?)",
                    "Atom01 Member 104Z", "legacy-104");
            var restarted = new ObjektChainMintService(jdbc, transaction, catalog, market,
                    rpc, "2741", CONTRACT, "0x" + SPIN, 60, 200);
            try {
                restarted.collect();
            } finally {
                restarted.stopMetadataWorkers();
            }
            verify(market, times(2)).fetchExternalMintCounts();
            assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1003);
            assertThat(jdbc.queryForList("SELECT collection_id FROM objekt_catalog", String.class))
                    .containsExactlyInAnyOrder("Atom01 Member 101Z", "Atom01 Member 102Z", "Atom01 Member 103Z",
                            "Atom01 Member 104Z", "Atom01 Mem+ber 101Z", "Atom01 Mem+ber 103Z");
            String collisionWithStored = slugForCollection(jdbc, "Atom01 Mem+ber 101Z");
            String collisionInBatch = slugForCollection(jdbc, "Atom01 Mem+ber 103Z");
            assertThat(collisionWithStored).startsWith("atom01-member-101z-");
            assertThat(collisionInBatch).startsWith("atom01-member-103z-");
            assertThat(jdbc.queryForList("SELECT slug FROM objekt_mint_event WHERE block_number = 1002", String.class))
                    .containsExactlyInAnyOrder("atom01-member-103z", "atom01-member-103z", "legacy-104",
                            collisionWithStored, collisionInBatch);
            assertThat(jdbc.queryForObject("SELECT new_mints FROM objekt_mint_total WHERE slug = 'legacy-104'",
                    Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT new_mints FROM objekt_mint_total WHERE slug = 'atom01-member-101z'",
                    Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT SUM(new_mints) FROM objekt_mint_total", Long.class)).isEqualTo(7);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void seedsAndAuditsLocalSlugsByExternalCollectionNameWhilePreservingExistingBaseline() throws Exception {
        var server = rpcServer(new ObjectMapper(), request -> switch (request.path("method").asText()) {
            case "eth_chainId" -> "0xab5";
            case "eth_blockNumber" -> hex(1000);
            default -> throw new AssertionError(request);
        });
        var logger = (Logger) LoggerFactory.getLogger(ObjektChainMintService.class);
        var previousLevel = logger.getLevel();
        var auditLog = new ListAppender<ILoggingEvent>();
        auditLog.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(auditLog);
        try {
            var source = database(false);
            var jdbc = new JdbcTemplate(source);
            String plain = "Atom01 Member 101Z";
            String collision = "Atom01 Mem+ber 101Z";
            String localPlain = "atom01-member-101z";
            String localCollision = "atom01-member-101z-local-suffix";
            jdbc.update("INSERT INTO objekt_catalog (collection_id, slug) VALUES (?, ?)", plain, localPlain);
            jdbc.update("INSERT INTO objekt_catalog (collection_id, slug) VALUES (?, ?)", collision, localCollision);
            jdbc.update("INSERT INTO objekt_mint_total (slug, baseline_count) VALUES (?, 11)", localPlain);

            var catalog = mock(ObjektService.class);
            var market = mock(ObjektMarketDataService.class);
            var externalPlain = externalCollection("atom01-member-101z", "atom01 member 101z");
            var externalCollision = externalCollection("external-collision", collision);
            when(catalog.fetchExternalCatalog()).thenReturn(List.of(externalPlain, externalCollision));
            when(catalog.findStoredByCollectionName(externalPlain.getCollectionId()))
                    .thenReturn(Map.of("collectionId", plain, "slug", localPlain));
            when(catalog.findStoredByCollectionName(collision))
                    .thenReturn(Map.of("collectionId", collision, "slug", localCollision));
            when(market.fetchExternalMintCounts()).thenReturn(
                    Map.of(externalPlain.getSlug(), 99L, externalCollision.getSlug(), 29L),
                    Map.of(externalPlain.getSlug(), 11L, externalCollision.getSlug(), 29L));
            var collector = new ObjektChainMintService(jdbc,
                    new TransactionTemplate(new DataSourceTransactionManager(source)), catalog, market,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "2741", CONTRACT, "0x" + SPIN, 60, 200);
            try {
                collector.collect();
                collector.audit();
            } finally {
                collector.stopMetadataWorkers();
            }

            assertThat(jdbc.queryForList("SELECT slug FROM objekt_mint_total", String.class))
                    .containsExactlyInAnyOrder(localPlain, localCollision);
            assertThat(jdbc.queryForObject("SELECT baseline_count FROM objekt_mint_total WHERE slug = ?", Long.class,
                    localPlain)).isEqualTo(11);
            assertThat(jdbc.queryForObject("SELECT baseline_count FROM objekt_mint_total WHERE slug = ?", Long.class,
                    localCollision)).isEqualTo(29);
            assertThat(slugForCollection(jdbc, collision)).isEqualTo(localCollision);
            assertThat(auditLog.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("오브젝트 일일 대조: 카탈로그 누락 0, 수량 차이 0, 공급량 slug 모호 0, 예시 [], 모호 slug 예시 []");
        } finally {
            logger.detachAppender(auditLog);
            logger.setLevel(previousLevel);
            auditLog.stop();
            server.stop(0);
        }
    }

    @Test
    void excludesExternalSupplyThatCouldBelongToEitherOfTwoLocalSlugs() throws Exception {
        var server = rpcServer(new ObjectMapper(), request -> switch (request.path("method").asText()) {
            case "eth_chainId" -> "0xab5";
            case "eth_blockNumber" -> hex(1000);
            default -> throw new AssertionError(request);
        });
        var logger = (Logger) LoggerFactory.getLogger(ObjektChainMintService.class);
        var previousLevel = logger.getLevel();
        var auditLog = new ListAppender<ILoggingEvent>();
        auditLog.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(auditLog);
        try {
            var source = database(false);
            var jdbc = new JdbcTemplate(source);
            var catalog = mock(ObjektService.class);
            var market = mock(ObjektMarketDataService.class);
            var first = externalCollection("shared-slug", "Atom01 Member 101Z");
            var second = externalCollection("shared-slug", "Atom01 Mem+ber 101Z");
            var other = externalCollection("clear-slug", "Atom01 Member 102Z");
            var sameName = externalCollection("clear-slug", "  ATOM01   MEMBER 102Z ");
            for (var row : List.of(first, second, other)) {
                String slug = row == second ? "shared-slug-local-suffix" : row.getSlug();
                jdbc.update("INSERT INTO objekt_catalog (collection_id, slug) VALUES (?, ?)", row.getCollectionId(), slug);
                when(catalog.findStoredByCollectionName(row.getCollectionId()))
                        .thenReturn(Map.of("collectionId", row.getCollectionId(), "slug", slug));
            }
            when(catalog.findStoredByCollectionName(sameName.getCollectionId()))
                    .thenReturn(Map.of("collectionId", other.getCollectionId(), "slug", other.getSlug()));
            when(catalog.fetchExternalCatalog()).thenReturn(List.of(first, second, other, sameName));
            when(market.fetchExternalMintCounts()).thenReturn(Map.of("shared-slug", 29L, "clear-slug", 11L));
            var collector = new ObjektChainMintService(jdbc,
                    new TransactionTemplate(new DataSourceTransactionManager(source)), catalog, market,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "2741", CONTRACT, "0x" + SPIN, 60, 200);
            try {
                collector.collect();
                collector.audit();
            } finally {
                collector.stopMetadataWorkers();
            }
            assertThat(jdbc.queryForList("SELECT slug FROM objekt_mint_total", String.class))
                    .containsExactly("clear-slug");
            assertThat(jdbc.queryForObject("SELECT baseline_count FROM objekt_mint_total", Long.class)).isEqualTo(11);
            assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1001);
            assertThat(auditLog.list).extracting(ILoggingEvent::getFormattedMessage).contains(
                    "오브젝트 초기 기준 수량에서 모호한 외부 slug 1개 제외: [shared-slug]",
                    "오브젝트 일일 대조: 카탈로그 누락 0, 수량 차이 0, 공급량 slug 모호 1, 예시 [], 모호 slug 예시 [shared-slug]");
        } finally {
            logger.detachAppender(auditLog);
            logger.setLevel(previousLevel);
            auditLog.stop();
            server.stop(0);
        }
    }

    private static ObjektCatalog externalCollection(String slug, String name) {
        var external = new ObjektCatalog();
        external.setSlug(slug);
        external.setCollectionId(name);
        return external;
    }

    @Test
    void rejectsAnUpsertMatchWithADifferentNormalizedName(CapturedOutput output) throws Exception {
        var mapper = new ObjectMapper();
        var server = rpcServer(mapper, request -> switch (request.path("method").asText()) {
            case "eth_chainId" -> "0xab5";
            case "eth_blockNumber" -> hex(1202);
            case "eth_getBlockByNumber" -> Map.of("timestamp", "0x654a2abd", "hash", blockHash(0));
            case "eth_getLogs" -> {
                var filter = request.path("params").path(0);
                yield filter.path("topics").path(1).isTextual()
                        && Long.decode(filter.path("fromBlock").asText()) == 1001
                        ? List.of(log(1001, 0, 3, ZERO, OWNER, 0)) : List.of();
            }
            default -> throw new AssertionError(request);
        });
        try {
            var source = database(true);
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO objekt_mint_cursor (chain_id, contract, next_block) VALUES ('2741', ?, 1001)", CONTRACT);
            jdbc.update("INSERT INTO objekt_catalog (collection_id, slug) VALUES (?, ?)",
                    "Atom01 Mémber 103Z", "legacy-accent");
            var market = mock(ObjektMarketDataService.class);
            when(market.fetchTokenMetadata("3")).thenReturn(mapper.readTree(metadata("103Z", "3")));
            var collector = collector(source, market, server);
            try {
                collector.collect();
            } finally {
                collector.stopMetadataWorkers();
            }

            assertThat(output).contains("블록 1001~1142", "DB 컬렉션 이름 불일치",
                    "요청 'Atom01 Member 103Z', 저장 'Atom01 Mémber 103Z'");
            assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1001);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_event", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_total", Long.class)).isZero();
            assertThat(jdbc.queryForList("SELECT slug FROM objekt_catalog", String.class)).containsExactly("legacy-accent");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewCachesStayBoundedAndExpireEvenWhenTheTipCheckFails() throws Exception {
        var mapper = new ObjectMapper();
        var tipReads = new AtomicInteger();
        var server = rpcServer(mapper, request -> switch (request.path("method").asText()) {
            case "eth_getBlockByNumber" -> Map.of("timestamp", "0x654a2abd", "hash", blockHash(tipReads.getAndIncrement()));
            case "eth_getLogs" -> List.of(log(1000, 0, 1, ZERO, OWNER, 0), log(1000, 1, 2, ZERO, OWNER, 0));
            default -> throw new AssertionError(request);
        });
        try {
            var market = mock(ObjektMarketDataService.class);
            when(market.fetchTokenMetadata("1")).thenReturn(mapper.readTree(metadata("101Z", "1")));
            when(market.fetchTokenMetadata("2")).thenThrow(new IllegalStateException("not ready"));
            var collector = collector(database(false), market, server);
            try {
                var metadata = (Map<AbstractTransferLog, Object>) ReflectionTestUtils.getField(collector, "previewMetadata");
                var retryAt = (Map<AbstractTransferLog, Long>) ReflectionTestUtils.getField(collector, "metadataRetryAt");
                var startingRows = AbstractTransferLog.parse(mapper.valueToTree(IntStream.range(0, 2_000)
                        .mapToObj(i -> log(998, i, i + 3, ZERO, OWNER, 0)).toList()));
                startingRows.forEach(row -> {
                    metadata.put(row, new Object());
                    retryAt.put(row, 1L);
                });
                assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(collector, "collectPreview", 1000L))
                        .hasMessageContaining("최근 블록이 조회 중 교체되었습니다.");
                assertThat(metadata).hasSize(2_000).doesNotContainKey(startingRows.get(0));
                assertThat(retryAt).hasSize(2_000).doesNotContainKey(startingRows.get(0));

                // Old logs from this mock RPC violate the newer window before prepare runs.
                assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(collector, "collectPreview", 1400L))
                        .hasMessageContaining("RPC 이벤트 값이 올바르지 않습니다.");
                assertThat(metadata).isEmpty();
                assertThat(retryAt).isEmpty();
            } finally {
                collector.stopMetadataWorkers();
            }
        } finally {
            server.stop(0);
        }
    }

    private static DriverManagerDataSource database(boolean accentInsensitive) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        if (accentInsensitive) new JdbcTemplate(source).execute("SET COLLATION ENGLISH STRENGTH PRIMARY");
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V29__upload_blob_and_objekt_catalog.sql"),
                new ClassPathResource("db/migration/V202611241200__objekt_chain_mints.sql")).execute(source);
        return source;
    }

    private static ObjektChainMintService collector(DriverManagerDataSource source, ObjektMarketDataService market,
                                                    HttpServer server) {
        return new ObjektChainMintService(new JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), mock(ObjektService.class),
                market, "http://127.0.0.1:" + server.getAddress().getPort(),
                "2741", CONTRACT, "0x" + SPIN, 60, 200);
    }

    private static HttpServer rpcServer(ObjectMapper mapper,
                                        Function<JsonNode, Object> respond) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Object result = respond.apply(mapper.readTree(exchange.getRequestBody()));
            byte[] body = mapper.writeValueAsBytes(Map.of("id", 1, "jsonrpc", "2.0", "result", result));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private static String slugForCollection(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("SELECT slug FROM objekt_catalog WHERE collection_id = ?", String.class, name);
    }

    private static String metadata(String number, String token) {
        return """
                {"name":"Atom01 Member %s #%s","image":"https://example.com/original/card.png",
                "attributes":[{"trait_type":"Artist","value":"tripleS"},{"trait_type":"Season","value":"Atom01"},
                {"trait_type":"Member","value":"Member"},{"trait_type":"Class","value":"First"},
                {"trait_type":"Collection","value":"%s"}]}
                """.formatted(number, token, number);
    }

    private static Map<String, Object> log(long block, int index, int token, String from, String to, int fork) {
        return Map.of("blockNumber", hex(block), "logIndex", hex(index), "transactionHash", "0x" + "e".repeat(64),
                "blockHash", blockHash(fork), "topics", List.of(TRANSFER, "0x" + "0".repeat(24) + from, "0x" + "0".repeat(24) + to, hex(token)));
    }

    private static String blockHash(int fork) {
        return "0x" + String.valueOf(fork).repeat(64);
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }
}

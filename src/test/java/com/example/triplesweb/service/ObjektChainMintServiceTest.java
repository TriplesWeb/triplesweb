package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjektChainMintServiceTest {
    private static final String ZERO = "0".repeat(40);
    private static final String SPIN = "a".repeat(40);
    private static final String OWNER = "b".repeat(40);
    private static final String CONTRACT = "0x" + "c".repeat(40);
    private static final String TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @Test
    void previewsMintsRollsBackReorgsAndConfirmsWithoutCountingTwice() throws Exception {
        var mapper = new ObjectMapper();
        var head = new AtomicLong(1000);
        var fork = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            var filter = request.path("params").path(0);
            Object result = switch (request.path("method").asText()) {
                case "eth_chainId" -> "0xab5";
                case "eth_blockNumber" -> hex(head.get());
                case "eth_getBlockByNumber" -> Map.of("timestamp", "0x654a2abd", "hash", blockHash(fork.get()));
                case "eth_getLogs" -> {
                    boolean mintFilter = filter.path("topics").path(1).isTextual();
                    long start = Long.decode(filter.path("fromBlock").asText());
                    long end = Long.decode(filter.path("toBlock").asText());
                    if (start <= 1001 && end >= 1001 && fork.get() != 1) {
                        yield mintFilter
                                ? List.of(log(1001, 0, 1, ZERO, OWNER, fork.get()),
                                          log(1001, 1, 2, ZERO, OWNER, fork.get()))
                                : List.of(log(1001, 2, 1, OWNER, SPIN, fork.get()),
                                          log(1001, 3, 2, OWNER, ZERO, fork.get()));
                    }
                    yield mintFilter && start <= 1002 && end >= 1002 && head.get() >= 1062
                            ? List.of(log(1002, 0, 1, ZERO, OWNER, fork.get())) : List.of();
                }
                default -> throw new AssertionError(request);
            };
            byte[] body = mapper.writeValueAsBytes(Map.of("id", 1, "jsonrpc", "2.0", "result", result));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        try {
            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() +
                    ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V202611241200__objekt_chain_mints.sql")).execute(source);
            var jdbc = new JdbcTemplate(source);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            var catalog = mock(ObjektService.class);
            var repository = mock(ObjektCatalogRepository.class);
            var market = mock(ObjektMarketDataService.class);
            Map<String, Object> existing = Map.of("slug", "atom01-member-101z",
                    "collectionId", "Atom01 Member 101Z");
            var external = new ObjektCatalog();
            external.setSlug("atom01-member-101z");
            external.setCollectionId("Atom01 Member 101Z");
            when(catalog.findCollections("triples")).thenReturn(List.of(existing));
            when(catalog.findStoredByKey("Atom01 Member 101Z")).thenReturn(existing);
            when(catalog.fetchExternalCatalog()).thenReturn(List.of(external));
            when(market.fetchExternalMintCounts()).thenReturn(Map.of("atom01-member-101z", 7L, "other-artist", 200L));
            when(market.fetchTokenMetadata("1")).thenReturn(mapper.readTree(metadata("101Z", "1")));
            when(market.fetchTokenMetadata("2")).thenReturn(mapper.readTree(metadata("102Z", "2")));
            String rpc = "http://127.0.0.1:" + server.getAddress().getPort();
            var collector = new ObjektChainMintService(jdbc, transaction, catalog, repository, market,
                    rpc, "2741", CONTRACT, "0x" + SPIN, 60, 200);
            collector.collect();
            verify(market).installMintCounts(Map.of("atom01-member-101z", 7L));
            try {
                head.set(1001);
                collector.collect();
                verify(market).installMintCounts(Map.of("atom01-member-101z", 8L, "atom01-member-102z", 1L));
                verify(catalog).publishPendingCollections(argThat(rows -> rows.size() == 1
                        && "atom01-member-102z".equals(rows.get(0).getSlug())));
                verify(repository, never()).saveAll(anyList());
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

                head.set(1061);
                collector.collect();
                assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1001);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_event", Long.class)).isZero();

                doReturn(mapper.readTree(metadata("102Z", "2"))).when(market).fetchTokenMetadata("2");
                fork.set(3);
                head.set(1060);
                collector.collect();
                verify(market, times(2)).installMintCounts(Map.of("atom01-member-101z", 8L, "atom01-member-102z", 1L));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_mint_event", Long.class)).isZero();

                head.set(1061);
                collector.collect();
            } finally {
                collector.stopMetadataWorkers();
            }
            assertThat(jdbc.queryForList("SELECT event_kind FROM objekt_mint_event", String.class))
                    .containsExactlyInAnyOrder("mint", "mint", "spin", "burn");
            verify(repository).saveAll(argThat(collections -> {
                ObjektCatalog added = collections.iterator().next();
                return "atom01-member-102z".equals(added.getSlug());
            }));
            verify(market, times(2)).installMintCounts(Map.of("atom01-member-101z", 8L, "atom01-member-102z", 1L));
            verify(market, never()).installMintCounts(argThat(counts ->
                    counts.getOrDefault("atom01-member-101z", 0L) > 8
                            || counts.getOrDefault("atom01-member-102z", 0L) > 1));

            when(market.fetchExternalMintCounts()).thenReturn(Map.of("atom01-member-101z", 4L));
            collector.audit();
            verify(catalog).enrichChainBackImages(List.of(external));
            assertThat(jdbc.queryForObject("SELECT baseline_count FROM objekt_mint_total WHERE slug = 'atom01-member-101z'",
                    Long.class)).isEqualTo(7);

            head.set(1062);
            var restarted = new ObjektChainMintService(jdbc, transaction, catalog, repository, market,
                    rpc, "2741", CONTRACT, "0x" + SPIN, 60, 200);
            try {
                restarted.collect();
            } finally {
                restarted.stopMetadataWorkers();
            }
            verify(market, times(2)).fetchExternalMintCounts();
            assertThat(jdbc.queryForObject("SELECT next_block FROM objekt_mint_cursor", Long.class)).isEqualTo(1003);
            assertThat(jdbc.queryForObject("SELECT SUM(new_mints) FROM objekt_mint_total", Long.class)).isEqualTo(2);
        } finally {
            server.stop(0);
        }
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

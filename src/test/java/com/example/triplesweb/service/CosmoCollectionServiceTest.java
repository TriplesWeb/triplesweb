package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CosmoCollectionServiceTest {
    @Test void keepsOnlyTokensStillHeldByTheWallet() {
        String wallet = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String other = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        var logs = List.of(
                new AbstractTransferLog(10, 1, "0x1", 100, zero(), wallet, null),
                new AbstractTransferLog(11, 1, "0x2", 200, zero(), wallet, null),
                new AbstractTransferLog(12, 1, "0x3", 100, wallet, other, null),
                new AbstractTransferLog(13, 1, "0x4", 100, other, wallet, null)
        );
        assertThat(CosmoCollectionService.currentlyHeld(logs, wallet)).containsExactly(100L, 200L);
    }

    private static String zero() {
        return "0x0000000000000000000000000000000000000000";
    }
}

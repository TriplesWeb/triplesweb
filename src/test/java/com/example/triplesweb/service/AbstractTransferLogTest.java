package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractTransferLogTest {
    @Test void parsesTransferTopicsIntoOwnerFields() throws Exception {
        var logs = new ObjectMapper().readTree("""
                [{"removed":false,"blockNumber":"0xf00001","logIndex":"0x5","transactionHash":"0xABC",
                  "blockTimestamp":"0x10",
                  "topics":["0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                    "0x0000000000000000000000000000000000000000000000000000000000000000",
                    "0x000000000000000000000000d59ac0af50bc780fd67e8c2b9b243500d46da70a",
                    "0x0000000000000000000000000000000000000000000000000000000000d2f561"]}]
                """);
        var parsed = AbstractTransferLog.parse(logs);
        assertThat(parsed).singleElement().satisfies(row -> {
            assertThat(row.blockNumber()).isEqualTo(0xf00001L);
            assertThat(row.logIndex()).isEqualTo(5);
            assertThat(row.tokenId()).isEqualTo(0xd2f561L);
            assertThat(row.toAddress()).isEqualTo("0xd59ac0af50bc780fd67e8c2b9b243500d46da70a");
            assertThat(row.fromAddress()).isEqualTo("0x0000000000000000000000000000000000000000");
        });
    }
}

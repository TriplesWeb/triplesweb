package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ObjektActivityClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String A = "0x" + "a".repeat(40), B = "0x" + "b".repeat(40), H = "0x" + "c".repeat(64);

    private String item(String id, String time, String serial) {
        return "{\"transfer\":{\"id\":\"" + id + "\",\"from\":\"" + A + "\",\"to\":\"" + B
                + "\",\"hash\":\"" + H + "\",\"timestamp\":\"" + time
                + "\"},\"objekt\":{\"tokenId\":\"123\",\"slug\":\"sample\",\"collectionId\":\"C1\",\"artist\":\"triples\",\"serial\":" + serial + "}}";
    }

    @Test void nullSerialIsNotInventedFromTokenId() throws Exception {
        var page = ObjektActivityClient.parse(mapper.readTree("{\"items\":[" + item("z", "2026-10-26T00:00:00Z", "0") + "]}"), null);
        assertThat(page.events().get(0).serial()).isNull();
        assertThat(page.events().get(0).tokenId()).isEqualTo("123");
    }

    @Test void invalidLaterItemRejectsWholePage() throws Exception {
        String json = "{\"items\":[" + item("z", "2026-10-26T00:00:00Z", "7") + ","
                + item("y", "2026-10-26T00:00:01Z", "8") + "]}";
        assertThatThrownBy(() -> ObjektActivityClient.parse(mapper.readTree(json), null)).isInstanceOf(IllegalStateException.class);
    }

    @Test void cursorMustBeLastItemAndMoveBackwards() throws Exception {
        String json = "{\"items\":[" + item("z", "2026-10-26T00:00:00Z", "7")
                + "],\"nextCursor\":{\"timestamp\":\"2026-10-26T00:00:00Z\",\"id\":\"z\"}}";
        assertThat(ObjektActivityClient.parse(mapper.readTree(json), null).next().id()).isEqualTo("z");
        assertThatThrownBy(() -> ObjektActivityClient.parse(mapper.readTree(json),
                new ObjektActivityClient.Cursor("2026-10-26T00:00:00Z", "z"))).isInstanceOf(IllegalStateException.class);
    }
}

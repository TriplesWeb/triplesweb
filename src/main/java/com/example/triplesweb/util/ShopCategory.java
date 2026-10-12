package com.example.triplesweb.util;

import lombok.Getter;

@Getter
public enum ShopCategory {
    ALBUM("album"),
    MERCH("merch"),
    PHOTOBOOK("photobook"),
    MAGAZINE("magazine"),
    TOUR("tour");
    private final String key;
    ShopCategory(String key) { this.key = key; }

    public static ShopCategory from (String raw) {
        if (raw == null) {
            return ALBUM;
        }
        String k = raw.toLowerCase();
        for (ShopCategory c : values()) {
            if (c.key.equals(k)) {
                return c;
            }
        }
        return ALBUM;
    }
}

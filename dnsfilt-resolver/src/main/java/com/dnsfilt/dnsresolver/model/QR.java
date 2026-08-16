package com.dnsfilt.dnsresolver.model;

/*
 * A one bit field that specifies whether this message is a query (0), or a response (1).
 */
public enum QR {
    QUERY(0), RESPONSE(1);

    private final int value;

    QR(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static QR fromValue(int value) {
        for (QR type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown QR value: " + value);
    }
}

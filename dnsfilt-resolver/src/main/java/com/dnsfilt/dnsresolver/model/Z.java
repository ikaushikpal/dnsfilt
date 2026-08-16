package com.dnsfilt.dnsresolver.model;

/*
 * Reserved for future use.  Must be zero in all queries and responses.
 */
public enum Z {
    ZERO(0);

    private final int value;

    Z(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Z fromValue(int value) {
        for (Z type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Z value: " + value);
    }
}

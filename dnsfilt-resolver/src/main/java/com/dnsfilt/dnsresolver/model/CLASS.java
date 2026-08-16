package com.dnsfilt.dnsresolver.model;

/*
 * a two octet code that specifies the class of the query.
 * For example, the QCLASS field is IN for the Internet.
 */
public enum CLASS {
    IN(1), // Internet
    CS(2), // CSNET (Obsolete)
    CH(3), // CHAOS
    HS(4); // Hesiod

    private final int value;

    CLASS(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CLASS fromValue(int value) {
        for (CLASS qclass : values()) {
            if (qclass.value == value) {
                return qclass;
            }
        }
        throw new IllegalArgumentException("Unknown CLASS value: " + value);
    }
}

package com.dnsfilt.dnsresolver.model;



 /*
     * A four bit field that specifies kind of query in this message.  
     * This value is set by the originator of a query and copied into the response.
     * 0               a standard query (QUERY)

                1               an inverse query (IQUERY)

                2               a server status request (STATUS)

                3-15            reserved for future use
     * 
     */
public enum Opcode {
    QUERY(0), IQUERY(1), STATUS(2);

    private final int value;

    Opcode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Opcode fromValue(int value) {
        for (Opcode type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Opcode value: " + value);
    }
}
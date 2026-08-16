package com.dnsfilt.dnsresolver.model;

 /*
     * Response code - this 4 bit field is set as part of responses
     * The values have the following
                interpretation:
     * 0               No error condition

                1               Format error - The name server was
                                unable to interpret the query.

                2               Server failure - The name server was
                                unable to process this query due to a
                                problem with the name server.

                3               Name Error - Meaningful only for
                                responses from an authoritative name
                                server, this code signifies that the
                                domain name referenced in the query does
                                not exist.

                4               Not Implemented - The name server does
                                not support the requested kind of query.

                5               Refused - The name server refuses to
                                perform the specified operation for
                                policy reasons.  For example, a name
                                server may not wish to provide the
                                information to the particular requester,
                                or a name server may not wish to perform
                                a particular operation (e.g., zone transfer) for particular data.

                6-15            Reserved for future use.


     */
public enum RCODE {
    NO_ERROR(0), FORMAT_ERROR(1), SERVER_FAILURE(2), NAME_ERROR(3), NOT_IMPLEMENTED(4), REFUSED(5);

    private final int value;

    RCODE(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static RCODE fromValue(int value) {
        for (RCODE type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RCODE value: " + value);
    }
}

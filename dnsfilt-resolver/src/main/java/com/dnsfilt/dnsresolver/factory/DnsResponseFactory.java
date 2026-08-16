package com.dnsfilt.dnsresolver.factory;

import com.dnsfilt.dnsresolver.model.*;

/**
 * DnsResponseFactory
 * 
 * Factory Pattern implementation for constructing DNS response byte arrays.
 * Encapsulates the low-level byte manipulation and DNS header generation
 * for resolved records, blocked/sinkholed records, and DNS errors.
 */
public class DnsResponseFactory {

    private DnsResponseFactory() {
        // Prevent direct instantiation
    }

    /**
     * Builds a standard DNS successful response packet (NO_ERROR, 1 Answer).
     */
    public static byte[] createSuccessResponse(DNSHeader receivedHeader, DNSQuestion receivedQuestion, DNSResourceRecord responseRecord) {
        DNSHeader responseHeader = new DNSHeader(
                receivedHeader.getId(),
                QR.RESPONSE,
                receivedHeader.getOpcode(),
                false, // Authoritative Answer
                false, // Truncated
                receivedHeader.isRd(), // Recursion Desired
                false, // Recursion Available
                Z.ZERO,
                RCODE.NO_ERROR,
                1, // QDCOUNT (1 question)
                1, // ANCOUNT (1 answer)
                0, // NSCOUNT
                0  // ARCOUNT
        );

        byte[] headerBytes = responseHeader.toByteArray();
        byte[] questionBytes = receivedQuestion.toByteArray();
        byte[] recordBytes = responseRecord.toByteArray();

        int totalLen = headerBytes.length + questionBytes.length + recordBytes.length;
        byte[] responseBytes = new byte[totalLen];

        System.arraycopy(headerBytes, 0, responseBytes, 0, headerBytes.length);
        System.arraycopy(questionBytes, 0, responseBytes, headerBytes.length, questionBytes.length);
        System.arraycopy(recordBytes, 0, responseBytes, headerBytes.length + questionBytes.length, recordBytes.length);

        return responseBytes;
    }

    /**
     * Builds a DNS blocked/sinkholed or NXDOMAIN/SERVFAIL error response packet (0 Answers).
     */
    public static byte[] createErrorOrBlockedResponse(DNSHeader receivedHeader, DNSQuestion receivedQuestion, boolean isBlocked) {
        RCODE rcode = isBlocked ? RCODE.NO_ERROR : RCODE.NAME_ERROR; // NAME_ERROR = NXDOMAIN (3)

        DNSHeader errorResponseHeader = new DNSHeader(
                receivedHeader.getId(),
                QR.RESPONSE,
                receivedHeader.getOpcode(),
                false,
                false,
                receivedHeader.isRd(),
                false,
                Z.ZERO,
                rcode,
                1, // QDCOUNT (1 question)
                0, // ANCOUNT (0 answers)
                0,
                0
        );

        byte[] errHeader = errorResponseHeader.toByteArray();
        byte[] errQuestion = receivedQuestion.toByteArray();

        byte[] responseBytes = new byte[errHeader.length + errQuestion.length];
        System.arraycopy(errHeader, 0, responseBytes, 0, errHeader.length);
        System.arraycopy(errQuestion, 0, responseBytes, errHeader.length, errQuestion.length);

        return responseBytes;
    }
}

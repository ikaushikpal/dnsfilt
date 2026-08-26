package com.dnsfilt.dnsresolver.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit Tests for ProxyProtocolDecoder.
 * Tests PROXY v1 (text), PROXY v2 (binary), and raw DNS fallback.
 */
class ProxyProtocolDecoderTest {

    /** 12-byte PROXY v2 binary signature */
    private static final byte[] V2_SIG = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    /** Minimal DNS A-query for "google.com" (28 bytes) */
    private static final byte[] DNS_QUERY = {
        0x12, 0x34,                         // ID
        0x01, 0x00,                         // FLAGS (RD=1)
        0x00, 0x01,                         // QDCOUNT=1
        0x00, 0x00,                         // ANCOUNT=0
        0x00, 0x00,                         // NSCOUNT=0
        0x00, 0x00,                         // ARCOUNT=0
        0x06, 'g','o','o','g','l','e',      // 6 google
        0x03, 'c','o','m',                  // 3 com
        0x00,                               // null terminator
        0x00, 0x01,                         // QTYPE = A (1)
        0x00, 0x01                          // QCLASS = IN (1)
    };

    // =========================================================================
    // PROXY PROTOCOL V1 TESTS (Text / NGINX 1.20.1)
    // =========================================================================

    @Test
    @DisplayName("V1-01: PROXY v1 TCP4 + DNS (exact NGINX 1.20.1 format from tcpdump)")
    void testV1_tcp4Format() throws UnknownHostException {
        // As seen in tcpdump: "PROXY TCP4 183.87.202.75 10.0.0.222 61995 53\r\n" + DNS
        String header = "PROXY TCP4 183.87.202.75 10.0.0.222 61995 53\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] packet = new byte[headerBytes.length + DNS_QUERY.length];
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);
        System.arraycopy(DNS_QUERY, 0, packet, headerBytes.length, DNS_QUERY.length);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(1, result.proxyVersion);
        assertEquals(InetAddress.getByName("183.87.202.75"), result.sourceAddress);
        assertEquals(61995, result.sourcePort);
        assertEquals(InetAddress.getByName("10.0.0.222"), result.destinationAddress);
        assertEquals(53, result.destinationPort);
        assertEquals(headerBytes.length, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);

        // Verify payload bytes match exactly
        byte[] payload = Arrays.copyOfRange(packet, result.payloadOffset, result.payloadOffset + result.payloadLength);
        assertArrayEquals(DNS_QUERY, payload);
    }

    @Test
    @DisplayName("V1-02: PROXY v1 TCP6 + DNS")
    void testV1_tcp6Format() throws UnknownHostException {
        String header = "PROXY TCP6 2001:db8::1 2001:db8::2 44321 53\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] packet = new byte[headerBytes.length + DNS_QUERY.length];
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);
        System.arraycopy(DNS_QUERY, 0, packet, headerBytes.length, DNS_QUERY.length);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(1, result.proxyVersion);
        assertEquals(InetAddress.getByName("2001:db8::1"), result.sourceAddress);
        assertEquals(44321, result.sourcePort);
        assertEquals(InetAddress.getByName("2001:db8::2"), result.destinationAddress);
        assertEquals(53, result.destinationPort);
        assertEquals(headerBytes.length, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);
    }

    @Test
    @DisplayName("V1-03: PROXY v1 UNKNOWN + DNS")
    void testV1_unknownFormat() {
        String header = "PROXY UNKNOWN\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] packet = new byte[headerBytes.length + DNS_QUERY.length];
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);
        System.arraycopy(DNS_QUERY, 0, packet, headerBytes.length, DNS_QUERY.length);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(1, result.proxyVersion);
        assertNull(result.sourceAddress);
        assertEquals(0, result.sourcePort);
        assertEquals(headerBytes.length, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);
    }

    @Test
    @DisplayName("V1-04: Malformed PROXY v1 - missing CRLF -> returns null")
    void testV1_missingCrlf() {
        String header = "PROXY TCP4 183.87.202.75 10.0.0.222 61995 53"; // no \r\n
        byte[] packet = header.getBytes(StandardCharsets.US_ASCII);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    @Test
    @DisplayName("V1-05: Malformed PROXY v1 - invalid port numbers -> returns null")
    void testV1_invalidPort() {
        String header = "PROXY TCP4 183.87.202.75 10.0.0.222 99999 53\r\n"; // port > 65535
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] packet = new byte[headerBytes.length + DNS_QUERY.length];
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    @Test
    @DisplayName("V1-06: Malformed PROXY v1 - invalid IP address -> returns null")
    void testV1_invalidIp() {
        String header = "PROXY TCP4 999.999.999.999 10.0.0.222 61995 53\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] packet = new byte[headerBytes.length + DNS_QUERY.length];
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    // =========================================================================
    // RAW DNS TESTS (Fallback Mode)
    // =========================================================================

    @Test
    @DisplayName("RAW-01: Raw DNS packet without PROXY header -> raw DNS mode")
    void testRaw_rawDnsPacket() {
        var result = ProxyProtocolDecoder.decode(DNS_QUERY, 0, DNS_QUERY.length);

        assertNotNull(result);
        assertFalse(result.proxyDetected);
        assertEquals(0, result.proxyVersion);
        assertEquals(0, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);
        assertNull(result.sourceAddress);
        assertEquals(0, result.sourcePort);
    }

    // =========================================================================
    // PROXY PROTOCOL V2 TESTS (Binary)
    // =========================================================================

    private static byte[] buildV2IPv4Packet(byte[] srcIp, int srcPort,
                                            byte[] dstIp, int dstPort,
                                            byte[] dnsPayload) {
        int addrLen = 12;
        ByteBuffer buf = ByteBuffer.allocate(16 + addrLen + dnsPayload.length);
        buf.put(V2_SIG);
        buf.put((byte) 0x21); // version=2, command=PROXY
        buf.put((byte) 0x12); // AF_INET, DGRAM (UDP)
        buf.putShort((short) addrLen);
        buf.put(srcIp);
        buf.put(dstIp);
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.put(dnsPayload);
        return buf.array();
    }

    @Test
    @DisplayName("V2-01: Valid PROXY v2 IPv4 UDP + DNS -> decoded successfully")
    void testV2_validIPv4ProxyPacket() throws UnknownHostException {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildV2IPv4Packet(src, 63944, dst, 53, DNS_QUERY);

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(2, result.proxyVersion);
        assertEquals(InetAddress.getByAddress(src), result.sourceAddress);
        assertEquals(63944, result.sourcePort);
        assertEquals(InetAddress.getByAddress(dst), result.destinationAddress);
        assertEquals(53, result.destinationPort);
        assertEquals(28, result.payloadOffset); // 16 header + 12 addr
        assertEquals(DNS_QUERY.length, result.payloadLength);
    }

    @Test
    @DisplayName("V2-02: PROXY v2 with TLVs skipped correctly")
    void testV2_withTlvs() {
        byte[] src = {(byte) 192, (byte) 168, 1, 100};
        byte[] dst = {10, 0, 0, 53};
        byte[] tlv  = {0x04, 0x00, 0x03, 'f', 'o', 'o'};

        int addrLen = 12 + tlv.length;
        ByteBuffer buf = ByteBuffer.allocate(16 + addrLen + DNS_QUERY.length);
        buf.put(V2_SIG);
        buf.put((byte) 0x21); // v2 PROXY
        buf.put((byte) 0x12); // AF_INET DGRAM
        buf.putShort((short) addrLen);
        buf.put(src);
        buf.put(dst);
        buf.putShort((short) 12345);
        buf.putShort((short) 53);
        buf.put(tlv);
        buf.put(DNS_QUERY);

        var result = ProxyProtocolDecoder.decode(buf.array(), 0, buf.capacity());

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(16 + addrLen, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);
    }

    @Test
    @DisplayName("V2-03: Malformed PROXY v2 - truncated header -> returns null")
    void testV2_truncated() {
        byte[] truncated = new byte[14];
        System.arraycopy(V2_SIG, 0, truncated, 0, 12);
        truncated[12] = 0x21;
        truncated[13] = 0x12;

        var result = ProxyProtocolDecoder.decode(truncated, 0, truncated.length);
        assertNull(result);
    }

    @Test
    @DisplayName("V2-04: PROXY v2 unsupported transport protocol -> returns null")
    void testV2_invalidProtocol() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildV2IPv4Packet(src, 63944, dst, 53, DNS_QUERY);
        packet[13] = 0x19; // AF_INET | invalid protocol 9

        var result = ProxyProtocolDecoder.decode(packet, 0, packet.length);
        assertNull(result);
    }
}

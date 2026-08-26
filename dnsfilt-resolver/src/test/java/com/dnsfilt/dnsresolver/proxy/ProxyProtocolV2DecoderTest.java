package com.dnsfilt.dnsresolver.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProxyProtocolV2Decoder covering all 15 spec scenarios.
 *
 * Test data uses realistic DNS A-record query bytes for "google.com":
 *   ID=0x1234, FLAGS=0x0100 (RD set), QDCOUNT=1
 *   QNAME: 6 google 3 com 0 = [6,g,o,o,g,l,e, 3,c,o,m, 0]
 *   QTYPE=A(1), QCLASS=IN(1)
 *
 * PROXY v2 signature: 0D 0A 0D 0A 00 0D 0A 51 55 49 54 0A
 */
class ProxyProtocolV2DecoderTest {

    /** 12-byte PROXY v2 binary signature */
    private static final byte[] SIG = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    /** Minimal DNS A-query for "google.com" (28 bytes) */
    private static final byte[] DNS_QUERY = {
        // Header (12 bytes)
        0x12, 0x34,             // ID
        0x01, 0x00,             // FLAGS (QR=0, RD=1)
        0x00, 0x01,             // QDCOUNT=1
        0x00, 0x00,             // ANCOUNT=0
        0x00, 0x00,             // NSCOUNT=0
        0x00, 0x00,             // ARCOUNT=0
        // Question
        0x06, 'g','o','o','g','l','e',  // 6-char label
        0x03, 'c','o','m',              // 3-char label
        0x00,                           // end of QNAME
        0x00, 0x01,                     // QTYPE = A (1)
        0x00, 0x01                      // QCLASS = IN (1)
    };

    // -----------------------------------------------------------------------
    // Helper: build a valid PROXY v2 IPv4 header with the given src/dst
    // -----------------------------------------------------------------------
    private static byte[] buildIPv4ProxyPacket(byte[] srcIp, int srcPort,
                                                byte[] dstIp, int dstPort,
                                                byte[] dnsPayload) {
        // Fixed header = 16 bytes, address block = 12 bytes
        int addrLen = 12;
        ByteBuffer buf = ByteBuffer.allocate(16 + addrLen + dnsPayload.length);

        // Signature
        buf.put(SIG);
        // version=2 | command=PROXY(1)  → 0x21
        buf.put((byte) 0x21);
        // AF_INET(1) | DGRAM(2)         → 0x12
        buf.put((byte) 0x12);
        // declared length = 12 (address block only)
        buf.putShort((short) addrLen);
        // Address block
        buf.put(srcIp);
        buf.put(dstIp);
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        // DNS payload
        buf.put(dnsPayload);

        return buf.array();
    }

    // -----------------------------------------------------------------------
    // TEST 1: Raw DNS packet without PROXY header
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T01: Raw DNS packet (no PROXY header) → raw DNS mode, payloadOffset=0")
    void test01_rawDnsPacket() {
        var result = ProxyProtocolV2Decoder.decode(DNS_QUERY, 0, DNS_QUERY.length);

        assertNotNull(result);
        assertFalse(result.proxyDetected);
        assertEquals(0, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);
        assertNull(result.sourceAddress);
        assertEquals(0, result.sourcePort);
    }

    // -----------------------------------------------------------------------
    // TEST 2: Valid PROXY v2 IPv4 UDP + DNS → proxyDetected=true
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T02: Valid PROXY v2 IPv4 UDP + DNS → decoded successfully")
    void test02_validIPv4ProxyPacket() throws UnknownHostException {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(InetAddress.getByAddress(src), result.sourceAddress);
        assertEquals(InetAddress.getByAddress(dst), result.destinationAddress);
    }

    // -----------------------------------------------------------------------
    // TEST 3: Correct source IP = 183.87.202.75
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T03: Source IP correctly extracted as 183.87.202.75")
    void test03_correctSourceIp() throws UnknownHostException {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertEquals("183.87.202.75", result.sourceAddress.getHostAddress());
    }

    // -----------------------------------------------------------------------
    // TEST 4: Correct destination IP = 10.0.0.222
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T04: Destination IP correctly extracted as 10.0.0.222")
    void test04_correctDestinationIp() throws UnknownHostException {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertEquals("10.0.0.222", result.destinationAddress.getHostAddress());
    }

    // -----------------------------------------------------------------------
    // TEST 5: Correct DNS payload offset = 16 + 12 = 28
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T05: DNS payload offset = 28 (16 fixed header + 12 addr block)")
    void test05_correctPayloadOffset() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertEquals(28, result.payloadOffset);
    }

    // -----------------------------------------------------------------------
    // TEST 6: Correct DNS payload length = DNS_QUERY.length
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T06: DNS payload length matches original DNS_QUERY size")
    void test06_correctPayloadLength() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertEquals(DNS_QUERY.length, result.payloadLength);

        // Verify the DNS bytes at the reported offset match exactly
        byte[] extracted = Arrays.copyOfRange(packet, result.payloadOffset,
                result.payloadOffset + result.payloadLength);
        assertArrayEquals(DNS_QUERY, extracted);
    }

    // -----------------------------------------------------------------------
    // TEST 7: PROXY v2 with TLVs — TLVs correctly skipped
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T07: PROXY v2 with TLV extensions — TLVs skipped, DNS payload correct")
    void test07_proxyWithTlvs() {
        byte[] src = {(byte) 192, (byte) 168, 1, 100};
        byte[] dst = {10, 0, 0, 53};
        byte[] tlv  = {0x04, 0x00, 0x03, 'f', 'o', 'o'}; // arbitrary TLV (type=4, len=3, value="foo")

        int addrLen = 12 + tlv.length; // 12 addr bytes + 6 TLV bytes = 18

        ByteBuffer buf = ByteBuffer.allocate(16 + addrLen + DNS_QUERY.length);
        buf.put(SIG);
        buf.put((byte) 0x21);   // v2 PROXY
        buf.put((byte) 0x12);   // AF_INET DGRAM
        buf.putShort((short) addrLen);
        buf.put(src); buf.put(dst);
        buf.putShort((short) 12345);  // srcPort
        buf.putShort((short) 53);     // dstPort
        buf.put(tlv);
        buf.put(DNS_QUERY);
        byte[] packet = buf.array();

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        // DNS payload must start after fixed(16) + addrLen(18) = 34
        assertEquals(16 + addrLen, result.payloadOffset);
        assertEquals(DNS_QUERY.length, result.payloadLength);

        byte[] extracted = Arrays.copyOfRange(packet, result.payloadOffset,
                result.payloadOffset + result.payloadLength);
        assertArrayEquals(DNS_QUERY, extracted);
    }

    // -----------------------------------------------------------------------
    // TEST 8: Truncated PROXY header (< 16 bytes) → null (malformed)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T08: Truncated PROXY header → null (malformed, drop packet)")
    void test08_truncatedHeader() {
        // Only 14 bytes — signature(12) + ver/cmd(1) + fam(1) — missing 2-byte length
        byte[] truncated = new byte[14];
        System.arraycopy(SIG, 0, truncated, 0, 12);
        truncated[12] = 0x21;
        truncated[13] = 0x12;

        var result = ProxyProtocolV2Decoder.decode(truncated, 0, truncated.length);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    // TEST 9: Invalid signature → raw DNS fallback (not malformed)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T09: Invalid PROXY signature → treated as raw DNS packet")
    void test09_invalidSignature() {
        // Start with a valid packet but corrupt byte 3 of the signature
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);
        packet[3] = 0x00; // corrupt signature

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertFalse(result.proxyDetected);
        assertEquals(0, result.payloadOffset); // entire datagram treated as DNS
    }

    // -----------------------------------------------------------------------
    // TEST 10: Invalid version (version=1) → null
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T10: Invalid version (v1) → null (malformed)")
    void test10_invalidVersion() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);
        // Change version nibble to 1: 0x21 → 0x11
        packet[12] = 0x11;

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    // TEST 11: Invalid command (0x0F) → null
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T11: Unknown command byte → null (malformed)")
    void test11_invalidCommand() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);
        // version=2 | command=0x0F → 0x2F
        packet[12] = 0x2F;

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    // TEST 12: Invalid address family (AF=0x5) → null
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T12: Unknown address family (0x5) → null (malformed)")
    void test12_invalidAddressFamily() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);
        // AF_UNKNOWN(5) | DGRAM(2) → 0x52
        packet[13] = 0x52;

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    // TEST 13: Declared length > datagram length → null
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T13: Declared proxy length exceeds datagram → null (malformed)")
    void test13_declaredLengthTooLarge() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 53, DNS_QUERY);
        // Overwrite declared length bytes (offset 14-15) with a huge value (0x1000 = 4096)
        packet[14] = 0x10;
        packet[15] = 0x00;

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    // TEST 14: Zero-length DNS payload → valid result, payloadLength=0
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T14: Valid PROXY v2 header with zero-length DNS payload → payloadLength=0")
    void test14_zeroDnsPayload() {
        byte[] src = {1, 2, 3, 4};
        byte[] dst = {10, 0, 0, 53};
        byte[] emptyPayload = new byte[0];
        byte[] packet = buildIPv4ProxyPacket(src, 12345, dst, 53, emptyPayload);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(0, result.payloadLength);
        assertEquals(28, result.payloadOffset); // still 16+12
    }

    // -----------------------------------------------------------------------
    // TEST 15: High UDP port numbers (63944, 65535) — unsigned 16-bit
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("T15: High ports (63944, 65535) correctly decoded as unsigned 16-bit")
    void test15_highPorts() {
        byte[] src = {(byte) 183, (byte) 87, (byte) 202, 75};
        byte[] dst = {10, 0, 0, (byte) 222};

        // srcPort = 63944, dstPort = 65535
        byte[] packet = buildIPv4ProxyPacket(src, 63944, dst, 65535, DNS_QUERY);

        var result = ProxyProtocolV2Decoder.decode(packet, 0, packet.length);

        assertNotNull(result);
        assertTrue(result.proxyDetected);
        assertEquals(63944, result.sourcePort,      "srcPort must be 63944 (unsigned)");
        assertEquals(65535, result.destinationPort, "dstPort must be 65535 (unsigned)");
    }
}

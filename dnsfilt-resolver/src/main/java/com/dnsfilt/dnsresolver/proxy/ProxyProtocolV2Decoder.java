package com.dnsfilt.dnsresolver.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ProxyProtocolV2Decoder
 *
 * Decodes PROXY Protocol v2 headers prepended to UDP DNS datagrams by Nginx.
 *
 * Dual-mode: if no PROXY v2 signature is found, the datagram is treated as a
 * raw DNS packet (payloadOffset=0). This allows rolling deployment — Java can be
 * updated before Nginx is reconfigured with "proxy_protocol on".
 *
 * PROXY v2 wire format (fixed header = 16 bytes):
 *   Bytes  0–11  : 12-byte signature  \r\n\r\n\0\r\nQUIT\n
 *   Byte  12     : version (high nibble=2) | command (low nibble)
 *   Byte  13     : address family (high nibble) | protocol (low nibble)
 *   Bytes 14–15  : 2-byte uint16 length of variable portion (big-endian)
 *
 * For AF_INET (IPv4) PROXY command, the variable portion (12 bytes):
 *   Bytes  0– 3  : source IPv4
 *   Bytes  4– 7  : destination IPv4
 *   Bytes  8– 9  : source port (uint16 big-endian)
 *   Bytes 10–11  : destination port (uint16 big-endian)
 *
 * For AF_INET6 (IPv6) PROXY command, the variable portion (36 bytes):
 *   Bytes  0–15  : source IPv6
 *   Bytes 16–31  : destination IPv6
 *   Bytes 32–33  : source port (uint16 big-endian)
 *   Bytes 34–35  : destination port (uint16 big-endian)
 *
 * Any remaining bytes after the address block are TLV extensions — they are skipped.
 * DNS payload begins at: 16 + declaredProxyLength
 *
 * Spec references:
 *   https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt (Section 2.2)
 */
public final class ProxyProtocolV2Decoder {

    private static final Logger logger = LoggerFactory.getLogger(ProxyProtocolV2Decoder.class);

    /** 12-byte PROXY Protocol v2 binary signature */
    private static final byte[] PROXY_V2_SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    /** Minimum size of a valid PROXY v2 fixed header */
    private static final int FIXED_HEADER_LENGTH = 16;

    // Command values (low nibble of byte 12)
    private static final int COMMAND_LOCAL = 0x00;
    private static final int COMMAND_PROXY = 0x01;

    // Address family values (high nibble of byte 13)
    private static final int AF_UNSPEC  = 0x0;
    private static final int AF_INET    = 0x1; // IPv4
    private static final int AF_INET6   = 0x2; // IPv6
    private static final int AF_UNIX    = 0x3;

    // Address block sizes
    private static final int ADDR_BLOCK_IPV4 = 12; // 4+4+2+2
    private static final int ADDR_BLOCK_IPV6 = 36; // 16+16+2+2

    private ProxyProtocolV2Decoder() {
        // Utility class — not instantiable
    }

    /**
     * Result of decoding a PROXY v2 header from a UDP datagram.
     *
     * If PROXY v2 was detected:
     *   - sourceAddress / sourcePort   = original Internet client
     *   - destinationAddress / destinationPort = server (us)
     *   - payloadOffset / payloadLength = slice of the original buffer containing ONLY the DNS bytes
     *
     * If no PROXY v2 header was detected (raw DNS packet):
     *   - sourceAddress / sourcePort / destinationAddress / destinationPort = null / 0
     *   - payloadOffset = 0, payloadLength = receivedLength   (entire datagram is DNS)
     *   - proxyDetected = false
     */
    public static final class ProxyProtocolResult {
        public final InetAddress sourceAddress;
        public final int sourcePort;
        public final InetAddress destinationAddress;
        public final int destinationPort;
        /** Byte offset into the original buffer where the DNS payload starts. */
        public final int payloadOffset;
        /** Number of DNS payload bytes starting at payloadOffset. */
        public final int payloadLength;
        /** True if a valid PROXY v2 header was found and decoded. */
        public final boolean proxyDetected;

        private ProxyProtocolResult(InetAddress sourceAddress, int sourcePort,
                                    InetAddress destinationAddress, int destinationPort,
                                    int payloadOffset, int payloadLength, boolean proxyDetected) {
            this.sourceAddress     = sourceAddress;
            this.sourcePort        = sourcePort;
            this.destinationAddress = destinationAddress;
            this.destinationPort   = destinationPort;
            this.payloadOffset     = payloadOffset;
            this.payloadLength     = payloadLength;
            this.proxyDetected     = proxyDetected;
        }

        /** Convenience factory: raw DNS packet — no PROXY header. */
        static ProxyProtocolResult rawDns(int totalLength) {
            return new ProxyProtocolResult(null, 0, null, 0, 0, totalLength, false);
        }
    }

    /**
     * Decodes the PROXY Protocol v2 header from a UDP datagram buffer.
     *
     * This method never throws. On any malformed data it returns {@code null};
     * the caller must drop the packet.
     *
     * On a valid raw DNS datagram (no PROXY signature) it returns a result with
     * {@code proxyDetected=false} and {@code payloadOffset=0}.
     *
     * @param data   the raw receive buffer
     * @param offset start of meaningful data within the buffer
     * @param length number of valid bytes starting at offset
     * @return decoded result, or null if the packet is malformed PROXY v2
     */
    public static ProxyProtocolResult decode(byte[] data, int offset, int length) {
        if (data == null || length < 0 || offset < 0 || offset + length > data.length) {
            logger.warn("Invalid ProxyProtocolV2Decoder.decode call: null/invalid buffer parameters");
            return null;
        }

        // Not enough bytes to even hold the signature — must be raw DNS
        if (length < PROXY_V2_SIGNATURE.length) {
            return ProxyProtocolResult.rawDns(length);
        }

        // --- Signature detection (12 bytes) ---
        if (!hasProxyV2Signature(data, offset)) {
            // No signature → treat as plain DNS datagram (dual-mode backwards compat)
            return ProxyProtocolResult.rawDns(length);
        }

        // We have the signature — now we MUST have at least 16 bytes for the fixed header
        if (length < FIXED_HEADER_LENGTH) {
            logger.warn("Invalid PROXYv2 packet: signature present but header truncated (length={})", length);
            return null;
        }

        // --- Byte 12: version | command ---
        int verCmd = data[offset + 12] & 0xFF;
        int version = (verCmd >> 4) & 0xF;
        int command = verCmd & 0xF;

        if (version != 2) {
            logger.warn("Invalid PROXYv2 packet: unsupported version={}", version);
            return null;
        }

        // --- Byte 13: address family | protocol ---
        int famProt  = data[offset + 13] & 0xFF;
        int addrFamily = (famProt >> 4) & 0xF;
        // int protocol = famProt & 0xF;  // STREAM=1, DGRAM=2 — we accept both

        // --- Bytes 14–15: declared length of variable portion (big-endian uint16) ---
        int declaredLen = ((data[offset + 14] & 0xFF) << 8) | (data[offset + 15] & 0xFF);

        // Validate: fixed header (16) + declared variable portion must fit in received datagram
        if (FIXED_HEADER_LENGTH + declaredLen > length) {
            logger.warn("Invalid PROXYv2 packet: declared length {} exceeds datagram length {} (header=16)",
                    declaredLen, length);
            return null;
        }

        // DNS payload starts right after the full PROXY header (fixed + variable)
        int dnsPayloadOffset = offset + FIXED_HEADER_LENGTH + declaredLen;
        int dnsPayloadLength = length - FIXED_HEADER_LENGTH - declaredLen;

        // --- LOCAL command: no address info, treat datagram as health probe ---
        if (command == COMMAND_LOCAL) {
            // LOCAL packets are typically Nginx health-check frames; no client IP is available.
            // Return a raw-DNS result so the caller can attempt to parse a DNS query if present,
            // or drop it if the payload is empty.
            logger.debug("PROXYv2 LOCAL command — no original client address available");
            return new ProxyProtocolResult(null, 0, null, 0,
                    dnsPayloadOffset, dnsPayloadLength, true);
        }

        // --- PROXY command ---
        if (command != COMMAND_PROXY) {
            logger.warn("Invalid PROXYv2 packet: unknown command={}", command);
            return null;
        }

        // Determine required address block size based on AF
        int addrVariableOffset = offset + FIXED_HEADER_LENGTH; // start of variable portion

        switch (addrFamily) {
            case AF_INET -> {
                if (declaredLen < ADDR_BLOCK_IPV4) {
                    logger.warn("Invalid PROXYv2 packet: AF_INET declared length {} < {} (minimum IPv4 block)",
                            declaredLen, ADDR_BLOCK_IPV4);
                    return null;
                }
                return decodeIPv4(data, addrVariableOffset, dnsPayloadOffset, dnsPayloadLength);
            }
            case AF_INET6 -> {
                if (declaredLen < ADDR_BLOCK_IPV6) {
                    logger.warn("Invalid PROXYv2 packet: AF_INET6 declared length {} < {} (minimum IPv6 block)",
                            declaredLen, ADDR_BLOCK_IPV6);
                    return null;
                }
                return decodeIPv6(data, addrVariableOffset, dnsPayloadOffset, dnsPayloadLength);
            }
            case AF_UNSPEC, AF_UNIX -> {
                // AF_UNSPEC / AF_UNIX — no address bytes; treat as LOCAL
                logger.debug("PROXYv2 AF_UNSPEC/UNIX — skipping address block");
                return new ProxyProtocolResult(null, 0, null, 0,
                        dnsPayloadOffset, dnsPayloadLength, true);
            }
            default -> {
                logger.warn("Invalid PROXYv2 packet: unknown address family={}", addrFamily);
                return null;
            }
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static boolean hasProxyV2Signature(byte[] data, int offset) {
        for (int i = 0; i < PROXY_V2_SIGNATURE.length; i++) {
            if (data[offset + i] != PROXY_V2_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static ProxyProtocolResult decodeIPv4(byte[] data, int varOffset,
                                                   int dnsPayloadOffset, int dnsPayloadLength) {
        try {
            byte[] srcIpBytes = new byte[4];
            byte[] dstIpBytes = new byte[4];
            System.arraycopy(data, varOffset,     srcIpBytes, 0, 4);
            System.arraycopy(data, varOffset + 4, dstIpBytes, 0, 4);

            InetAddress srcAddr = InetAddress.getByAddress(srcIpBytes);
            InetAddress dstAddr = InetAddress.getByAddress(dstIpBytes);

            // Ports are unsigned 16-bit big-endian
            int srcPort = ((data[varOffset + 8]  & 0xFF) << 8) | (data[varOffset + 9]  & 0xFF);
            int dstPort = ((data[varOffset + 10] & 0xFF) << 8) | (data[varOffset + 11] & 0xFF);

            logger.debug("PROXYv2 client={}:{} destination={}:{} dnsPayloadLength={}",
                    srcAddr.getHostAddress(), srcPort,
                    dstAddr.getHostAddress(), dstPort,
                    dnsPayloadLength);

            return new ProxyProtocolResult(srcAddr, srcPort, dstAddr, dstPort,
                    dnsPayloadOffset, dnsPayloadLength, true);

        } catch (UnknownHostException e) {
            // InetAddress.getByAddress(4-byte[]) never actually throws — but handle defensively
            logger.warn("Invalid PROXYv2 packet: failed to parse IPv4 addresses: {}", e.getMessage());
            return null;
        }
    }

    private static ProxyProtocolResult decodeIPv6(byte[] data, int varOffset,
                                                   int dnsPayloadOffset, int dnsPayloadLength) {
        try {
            byte[] srcIpBytes = new byte[16];
            byte[] dstIpBytes = new byte[16];
            System.arraycopy(data, varOffset,      srcIpBytes, 0, 16);
            System.arraycopy(data, varOffset + 16, dstIpBytes, 0, 16);

            InetAddress srcAddr = InetAddress.getByAddress(srcIpBytes);
            InetAddress dstAddr = InetAddress.getByAddress(dstIpBytes);

            int srcPort = ((data[varOffset + 32] & 0xFF) << 8) | (data[varOffset + 33] & 0xFF);
            int dstPort = ((data[varOffset + 34] & 0xFF) << 8) | (data[varOffset + 35] & 0xFF);

            logger.debug("PROXYv2 (IPv6) client={}:{} destination={}:{} dnsPayloadLength={}",
                    srcAddr.getHostAddress(), srcPort,
                    dstAddr.getHostAddress(), dstPort,
                    dnsPayloadLength);

            return new ProxyProtocolResult(srcAddr, srcPort, dstAddr, dstPort,
                    dnsPayloadOffset, dnsPayloadLength, true);

        } catch (UnknownHostException e) {
            logger.warn("Invalid PROXYv2 packet: failed to parse IPv6 addresses: {}", e.getMessage());
            return null;
        }
    }
}

package com.dnsfilt.dnsresolver.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * ProxyProtocolDecoder
 *
 * Universal PROXY Protocol decoder supporting BOTH PROXY Protocol v1 (text) and
 * PROXY Protocol v2 (binary), with seamless fallback to raw DNS datagrams.
 *
 * Designed for NGINX stream (e.g. NGINX 1.20.1 sends "PROXY TCP4 ...\r\n") and
 * HAProxy / modern load balancers (sending PROXY v2 binary envelopes).
 *
 * PROXY v1 wire format (ASCII text, max 107 chars):
 *   "PROXY TCP4 <src-ip> <dst-ip> <src-port> <dst-port>\r\n"
 *   "PROXY TCP6 <src-ip> <dst-ip> <src-port> <dst-port>\r\n"
 *   "PROXY UNKNOWN\r\n" or "PROXY UNKNOWN ...\r\n"
 *
 * PROXY v2 wire format (16-byte fixed binary header + variable address block):
 *   \r\n\r\n\0\r\nQUIT\n (12 bytes) + ver/cmd (1 byte) + fam/prot (1 byte) + len (2 bytes uint16)
 *
 * Dual/Tri-mode:
 *   1. Starts with "PROXY " -> Decodes PROXY v1 text header
 *   2. Starts with \r\n\r\n\0\r\nQUIT\n -> Decodes PROXY v2 binary header
 *   3. Otherwise -> Returns raw DNS result (payloadOffset=0, payloadLength=length, proxyDetected=false)
 */
public final class ProxyProtocolDecoder {

    private static final Logger logger = LoggerFactory.getLogger(ProxyProtocolDecoder.class);

    /** 6-byte PROXY Protocol v1 ASCII signature: "PROXY " */
    private static final byte[] PROXY_V1_SIGNATURE = { 'P', 'R', 'O', 'X', 'Y', ' ' };

    /** Maximum allowed length for PROXY v1 header line per HAProxy spec (107 bytes) */
    private static final int MAX_PROXY_V1_HEADER_LENGTH = 108;

    /** 12-byte PROXY Protocol v2 binary signature */
    private static final byte[] PROXY_V2_SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    /** Minimum size of a valid PROXY v2 fixed header */
    private static final int V2_FIXED_HEADER_LENGTH = 16;

    // PROXY v2 Command values (low nibble of byte 12)
    private static final int COMMAND_LOCAL = 0x00;
    private static final int COMMAND_PROXY = 0x01;

    // PROXY v2 Address family values (high nibble of byte 13)
    private static final int AF_UNSPEC  = 0x0;
    private static final int AF_INET    = 0x1; // IPv4
    private static final int AF_INET6   = 0x2; // IPv6
    private static final int AF_UNIX    = 0x3;

    // PROXY v2 Transport protocol values (low nibble of byte 13)
    private static final int PROTOCOL_UNSPEC = 0x0;
    private static final int PROTOCOL_STREAM = 0x1; // TCP
    private static final int PROTOCOL_DGRAM  = 0x2; // UDP

    // Address block sizes for PROXY v2
    private static final int ADDR_BLOCK_IPV4 = 12; // 4 + 4 + 2 + 2
    private static final int ADDR_BLOCK_IPV6 = 36; // 16 + 16 + 2 + 2

    private ProxyProtocolDecoder() {
        // Utility class
    }

    /**
     * Result of decoding a PROXY header from a UDP datagram.
     */
    public static final class ProxyProtocolResult {
        public final InetAddress sourceAddress;
        public final int sourcePort;
        public final InetAddress destinationAddress;
        public final int destinationPort;
        public final int payloadOffset;
        public final int payloadLength;
        public final boolean proxyDetected;
        public final int proxyVersion; // 1 for v1, 2 for v2, 0 for none

        public ProxyProtocolResult(InetAddress sourceAddress, int sourcePort,
                                   InetAddress destinationAddress, int destinationPort,
                                   int payloadOffset, int payloadLength,
                                   boolean proxyDetected, int proxyVersion) {
            this.sourceAddress      = sourceAddress;
            this.sourcePort         = sourcePort;
            this.destinationAddress = destinationAddress;
            this.destinationPort    = destinationPort;
            this.payloadOffset      = payloadOffset;
            this.payloadLength      = payloadLength;
            this.proxyDetected      = proxyDetected;
            this.proxyVersion       = proxyVersion;
        }

        /** Convenience factory: raw DNS packet — no PROXY header detected. */
        public static ProxyProtocolResult rawDns(int totalLength) {
            return new ProxyProtocolResult(null, 0, null, 0, 0, totalLength, false, 0);
        }
    }

    /**
     * Decodes PROXY v1 or PROXY v2 header from a UDP datagram.
     *
     * Never throws exceptions. Returns null on malformed PROXY headers so the caller
     * can safely drop the packet without passing garbage to DNS decoders.
     * Returns a valid result with proxyDetected=false if no PROXY signature is found.
     *
     * @param data   the raw receive buffer
     * @param offset start of valid data within the buffer
     * @param length total number of valid bytes
     * @return decoded result, or null if malformed PROXY header
     */
    public static ProxyProtocolResult decode(byte[] data, int offset, int length) {
        if (data == null || length < 0 || offset < 0 || offset + length > data.length) {
            logger.warn("Invalid ProxyProtocolDecoder.decode call: buffer bounds mismatch");
            return null;
        }

        // 1. Check PROXY v1 signature ("PROXY ")
        if (hasSignature(data, offset, length, PROXY_V1_SIGNATURE)) {
            return decodeV1(data, offset, length);
        }

        // 2. Check PROXY v2 signature (\r\n\r\n\0\r\nQUIT\n)
        if (hasSignature(data, offset, length, PROXY_V2_SIGNATURE)) {
            return decodeV2(data, offset, length);
        }

        // 3. Fallback: Raw DNS packet without any PROXY header
        return ProxyProtocolResult.rawDns(length);
    }

    // =========================================================================
    // PROXY PROTOCOL V1 DECODER (Text / ASCII)
    // =========================================================================

    /**
     * Decodes PROXY Protocol v1 text line (e.g. "PROXY TCP4 183.87.202.75 10.0.0.222 61995 53\r\n")
     */
    private static ProxyProtocolResult decodeV1(byte[] data, int offset, int length) {
        // Find CRLF (\r\n) within MAX_PROXY_V1_HEADER_LENGTH bytes
        int crlfIndex = -1;
        int maxSearch = Math.min(length, MAX_PROXY_V1_HEADER_LENGTH);
        for (int i = 0; i < maxSearch - 1; i++) {
            if (data[offset + i] == '\r' && data[offset + i + 1] == '\n') {
                crlfIndex = i;
                break;
            }
        }

        if (crlfIndex == -1) {
            logger.warn("Invalid PROXYv1 packet: missing CRLF within {} bytes", maxSearch);
            return null;
        }

        // Extract header line as ASCII string
        String headerLine = new String(data, offset, crlfIndex, StandardCharsets.US_ASCII);
        int dnsPayloadOffset = offset + crlfIndex + 2; // right after \r\n
        int dnsPayloadLength = length - (crlfIndex + 2);

        String[] parts = headerLine.split(" ");
        if (parts.length < 2 || !parts[0].equals("PROXY")) {
            logger.warn("Invalid PROXYv1 format: '{}'", headerLine);
            return null;
        }

        String protocol = parts[1];

        // UNKNOWN format: "PROXY UNKNOWN\r\n" or "PROXY UNKNOWN ...\r\n"
        if ("UNKNOWN".equalsIgnoreCase(protocol)) {
            logger.debug("PROXYv1 UNKNOWN detected, payloadLength={}", dnsPayloadLength);
            return new ProxyProtocolResult(null, 0, null, 0,
                    dnsPayloadOffset, dnsPayloadLength, true, 1);
        }

        // TCP4 or TCP6: "PROXY TCP4 <src-ip> <dst-ip> <src-port> <dst-port>"
        if ("TCP4".equalsIgnoreCase(protocol) || "TCP6".equalsIgnoreCase(protocol)) {
            if (parts.length < 6) {
                logger.warn("Invalid PROXYv1 {} line (insufficient parts): '{}'", protocol, headerLine);
                return null;
            }

            try {
                String srcIpStr = parts[2];
                String dstIpStr = parts[3];
                int srcPort = Integer.parseInt(parts[4]);
                int dstPort = Integer.parseInt(parts[5]);

                if (srcPort < 0 || srcPort > 65535 || dstPort < 0 || dstPort > 65535) {
                    logger.warn("Invalid PROXYv1 port numbers: '{}'", headerLine);
                    return null;
                }

                InetAddress srcAddr = InetAddress.getByName(srcIpStr);
                InetAddress dstAddr = InetAddress.getByName(dstIpStr);

                logger.debug("PROXYv1 decoded: client={}:{} destination={}:{} dnsPayloadLength={}",
                        srcAddr.getHostAddress(), srcPort, dstAddr.getHostAddress(), dstPort, dnsPayloadLength);

                return new ProxyProtocolResult(srcAddr, srcPort, dstAddr, dstPort,
                        dnsPayloadOffset, dnsPayloadLength, true, 1);

            } catch (UnknownHostException | NumberFormatException e) {
                logger.warn("Failed to parse PROXYv1 addresses/ports from '{}': {}", headerLine, e.getMessage());
                return null;
            }
        }

        logger.warn("Unsupported PROXYv1 transport protocol '{}' in '{}'", protocol, headerLine);
        return null;
    }

    // =========================================================================
    // PROXY PROTOCOL V2 DECODER (Binary)
    // =========================================================================

    private static ProxyProtocolResult decodeV2(byte[] data, int offset, int length) {
        if (length < V2_FIXED_HEADER_LENGTH) {
            logger.warn("Invalid PROXYv2 packet: header truncated (length={})", length);
            return null;
        }

        // Byte 12: version | command
        int verCmd = data[offset + 12] & 0xFF;
        int version = (verCmd >> 4) & 0xF;
        int command = verCmd & 0xF;

        if (version != 2) {
            logger.warn("Invalid PROXYv2 packet: unsupported version={}", version);
            return null;
        }

        // Byte 13: address family | protocol
        int famProt    = data[offset + 13] & 0xFF;
        int addrFamily = (famProt >> 4) & 0xF;
        int protocol   = famProt & 0xF; // UNSPEC=0, STREAM=1 (TCP), DGRAM=2 (UDP)

        if (protocol != PROTOCOL_UNSPEC && protocol != PROTOCOL_STREAM && protocol != PROTOCOL_DGRAM) {
            logger.warn("Invalid PROXYv2 packet: unsupported transport protocol={}", protocol);
            return null;
        }

        // Bytes 14–15: declared length of variable portion (big-endian uint16)
        int declaredLen = ((data[offset + 14] & 0xFF) << 8) | (data[offset + 15] & 0xFF);

        if (V2_FIXED_HEADER_LENGTH + declaredLen > length) {
            logger.warn("Invalid PROXYv2 packet: declared length {} exceeds datagram length {} (header=16)",
                    declaredLen, length);
            return null;
        }

        int dnsPayloadOffset = offset + V2_FIXED_HEADER_LENGTH + declaredLen;
        int dnsPayloadLength = length - V2_FIXED_HEADER_LENGTH - declaredLen;

        // LOCAL command: health check frame, no address block
        if (command == COMMAND_LOCAL) {
            logger.debug("PROXYv2 LOCAL command detected, dnsPayloadLength={}", dnsPayloadLength);
            return new ProxyProtocolResult(null, 0, null, 0,
                    dnsPayloadOffset, dnsPayloadLength, true, 2);
        }

        if (command != COMMAND_PROXY) {
            logger.warn("Invalid PROXYv2 packet: unknown command={}", command);
            return null;
        }

        int addrVarOffset = offset + V2_FIXED_HEADER_LENGTH;

        switch (addrFamily) {
            case AF_INET -> {
                if (declaredLen < ADDR_BLOCK_IPV4) {
                    logger.warn("Invalid PROXYv2 packet: AF_INET declared length {} < {} (minimum IPv4 block)",
                            declaredLen, ADDR_BLOCK_IPV4);
                    return null;
                }
                return decodeV2IPv4(data, addrVarOffset, dnsPayloadOffset, dnsPayloadLength);
            }
            case AF_INET6 -> {
                if (declaredLen < ADDR_BLOCK_IPV6) {
                    logger.warn("Invalid PROXYv2 packet: AF_INET6 declared length {} < {} (minimum IPv6 block)",
                            declaredLen, ADDR_BLOCK_IPV6);
                    return null;
                }
                return decodeV2IPv6(data, addrVarOffset, dnsPayloadOffset, dnsPayloadLength);
            }
            case AF_UNSPEC, AF_UNIX -> {
                logger.debug("PROXYv2 AF_UNSPEC/UNIX detected, dnsPayloadLength={}", dnsPayloadLength);
                return new ProxyProtocolResult(null, 0, null, 0,
                        dnsPayloadOffset, dnsPayloadLength, true, 2);
            }
            default -> {
                logger.warn("Invalid PROXYv2 packet: unknown address family={}", addrFamily);
                return null;
            }
        }
    }

    private static ProxyProtocolResult decodeV2IPv4(byte[] data, int varOffset,
                                                    int dnsPayloadOffset, int dnsPayloadLength) {
        try {
            byte[] srcIpBytes = new byte[4];
            byte[] dstIpBytes = new byte[4];
            System.arraycopy(data, varOffset,     srcIpBytes, 0, 4);
            System.arraycopy(data, varOffset + 4, dstIpBytes, 0, 4);

            InetAddress srcAddr = InetAddress.getByAddress(srcIpBytes);
            InetAddress dstAddr = InetAddress.getByAddress(dstIpBytes);

            int srcPort = ((data[varOffset + 8]  & 0xFF) << 8) | (data[varOffset + 9]  & 0xFF);
            int dstPort = ((data[varOffset + 10] & 0xFF) << 8) | (data[varOffset + 11] & 0xFF);

            logger.debug("PROXYv2 (IPv4) client={}:{} destination={}:{} dnsPayloadLength={}",
                    srcAddr.getHostAddress(), srcPort, dstAddr.getHostAddress(), dstPort, dnsPayloadLength);

            return new ProxyProtocolResult(srcAddr, srcPort, dstAddr, dstPort,
                    dnsPayloadOffset, dnsPayloadLength, true, 2);

        } catch (UnknownHostException e) {
            logger.warn("Invalid PROXYv2 packet: failed to parse IPv4 addresses: {}", e.getMessage());
            return null;
        }
    }

    private static ProxyProtocolResult decodeV2IPv6(byte[] data, int varOffset,
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
                    srcAddr.getHostAddress(), srcPort, dstAddr.getHostAddress(), dstPort, dnsPayloadLength);

            return new ProxyProtocolResult(srcAddr, srcPort, dstAddr, dstPort,
                    dnsPayloadOffset, dnsPayloadLength, true, 2);

        } catch (UnknownHostException e) {
            logger.warn("Invalid PROXYv2 packet: failed to parse IPv6 addresses: {}", e.getMessage());
            return null;
        }
    }

    private static boolean hasSignature(byte[] data, int offset, int length, byte[] signature) {
        if (length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}

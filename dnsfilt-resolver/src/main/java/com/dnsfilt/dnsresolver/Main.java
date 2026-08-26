package com.dnsfilt.dnsresolver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dnsfilt.dnsresolver.config.AppConfig;
import com.dnsfilt.dnsresolver.factory.DnsResponseFactory;
import com.dnsfilt.dnsresolver.model.DNSHeader;
import com.dnsfilt.dnsresolver.model.DNSQuestion;
import com.dnsfilt.dnsresolver.model.DNSResourceRecord;
import com.dnsfilt.dnsresolver.model.TYPE;
import com.dnsfilt.dnsresolver.proxy.DnsClientInfo;
import com.dnsfilt.dnsresolver.proxy.ProxyProtocolV2Decoder;
import com.dnsfilt.dnsresolver.proxy.ProxyProtocolV2Decoder.ProxyProtocolResult;
import com.dnsfilt.dnsresolver.service.KafkaProducerService;
import com.dnsfilt.dnsresolver.utility.RedisManager;

/**
 * Main
 *
 * High-Throughput UDP DNS Server Entry Point.
 *
 * Architecture:
 * - Uses Java 21 Virtual Threads for non-blocking per-packet concurrency.
 * - SO_RCVBUF / SO_SNDBUF set to 8MB OS socket buffers for zero packet drops under traffic bursts.
 * - Employs DnsResponseFactory for binary DNS response serialization.
 * - Supports dual-mode PROXY Protocol v2: if Nginx prepends a PROXY v2 header, the real
 *   client IP is extracted from it; otherwise the datagram is treated as raw DNS (backwards-compat).
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Default fallback DNS UDP port
    private static final int DEFAULT_PORT = 2053;

    // 8 MB OS UDP Socket Buffer Size to handle high-throughput bursts without packet drops
    private static final int UDP_SOCKET_BUFFER_SIZE = 8 * 1024 * 1024;

    public static void main(String[] args) {
        // Disable Java SPI DNS hook so JVM internal networking (Jedis/Kafka) uses native system DNS
        System.setProperty("dnsjava.dns.spi.disabled", "true");

        AppConfig config = AppConfig.getInstance();
        int port = parsePort(config);

        logger.info("Starting DNS Engine (UDP Listener) on port {}...", port);

        // Initialize connection pools & services
        RedisManager.init();
        try {
            KafkaProducerService.getInstance();
        } catch (Exception e) {
            logger.warn("KafkaProducer initialization notice: {}. Continuing without Kafka streaming.", e.getMessage());
        }

        AdvancedDnsResolver dnsResolver = new AdvancedDnsResolver();

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
             DatagramSocket serverSocket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"))) {

            // Set high-performance OS socket receive & send buffers
            try {
                serverSocket.setReceiveBufferSize(UDP_SOCKET_BUFFER_SIZE);
                serverSocket.setSendBufferSize(UDP_SOCKET_BUFFER_SIZE);
                logger.info("Configured OS UDP socket buffers (SO_RCVBUF={}, SO_SNDBUF={})",
                        serverSocket.getReceiveBufferSize(), serverSocket.getSendBufferSize());
            } catch (Exception ex) {
                logger.warn("Notice setting UDP socket buffers: {}", ex.getMessage());
            }

            logger.info("DNS Engine listening for UDP packets on 0.0.0.0:{}", port);

            while (!Thread.currentThread().isInterrupted()) {
                byte[] requestBuffer = new byte[4096];
                DatagramPacket requestPacket = new DatagramPacket(requestBuffer, requestBuffer.length);
                serverSocket.receive(requestPacket);

                final int receivedLength = requestPacket.getLength();
                final byte[] packetData  = requestPacket.getData(); // do NOT copy — pass slice via offset/length

                // --- PROXY Protocol v2 detection ---
                // decode() returns null for malformed PROXY packets, or a result with
                // proxyDetected=false for plain DNS datagrams (no PROXY signature).
                final ProxyProtocolResult proxyResult = ProxyProtocolV2Decoder.decode(packetData, 0, receivedLength);
                if (proxyResult == null) {
                    // Malformed PROXY v2 header — drop silently (already logged inside decoder)
                    continue;
                }

                // Resolve the real client identity
                final DnsClientInfo clientInfo;
                if (proxyResult.proxyDetected && proxyResult.sourceAddress != null) {
                    // Real client IP from PROXY v2 header (behind Nginx)
                    clientInfo = new DnsClientInfo(proxyResult.sourceAddress, proxyResult.sourcePort);
                } else {
                    // No PROXY header — use the UDP packet's sender address directly
                    clientInfo = DnsClientInfo.fromSocketAddress(requestPacket.getSocketAddress());
                }

                final int dnsOffset = proxyResult.payloadOffset;
                final int dnsLength = proxyResult.payloadLength;

                // Dispatch to virtual thread with DNS-only slice
                CompletableFuture.runAsync(
                        () -> processPacket(serverSocket, clientInfo, packetData, dnsOffset, dnsLength, dnsResolver),
                        virtualExecutor);
            }
        } catch (IOException e) {
            logger.error("UDP Server exception: {}", e.getMessage(), e);
        }
    }

    private static int parsePort(AppConfig config) {
        String envPort = config.getEnvVariable("RESOLVER_PORT");
        if (envPort == null || envPort.trim().isEmpty()) {
            envPort = config.getEnvVariable("DNSFILT_RESOLVER_PORT");
        }
        if (envPort == null || envPort.trim().isEmpty()) {
            envPort = config.getEnvVariable("DNS_PORT");
        }
        if (envPort == null || envPort.trim().isEmpty()) {
            envPort = config.getEnvVariable("PORT");
        }
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT in environment '{}'. Falling back to default port {}.", envPort, DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }

    /**
     * Processes a single DNS datagram (after PROXY header has been stripped).
     *
     * @param socket     the server socket for sending the response
     * @param clientInfo resolved client identity (real IP from PROXY v2, or raw packet sender)
     * @param data       the full receive buffer (PROXY header + DNS payload)
     * @param dnsOffset  byte offset into data where the DNS payload starts
     * @param dnsLength  number of DNS payload bytes
     * @param dnsResolver the resolver to dispatch the question to
     */
    private static void processPacket(DatagramSocket socket, DnsClientInfo clientInfo,
                                      byte[] data, int dnsOffset, int dnsLength,
                                      AdvancedDnsResolver dnsResolver) {
        try {
            if (dnsLength < 12) {
                logger.warn("Received invalid DNS packet from {}: payload length {} < 12 bytes.",
                        clientInfo, dnsLength);
                return;
            }

            String clientIp = clientInfo.getIpString();

            // Parse DNS Header (12 bytes) from the DNS payload slice
            byte[] headerBuffer = Arrays.copyOfRange(data, dnsOffset, dnsOffset + 12);
            DNSHeader receivedHeader = DNSHeader.fromByteArray(headerBuffer);

            // Determine length of QNAME + QTYPE + QCLASS section
            int questionLength = DNSQuestion.getQuestionLength(data, dnsOffset + 12);
            int questionEnd = Math.min(dnsOffset + 12 + questionLength, dnsOffset + dnsLength);
            byte[] questionBuffer = Arrays.copyOfRange(data, dnsOffset + 12, questionEnd);

            // --- Guard: check for unknown/unsupported TYPE before parsing ---
            // QTYPE is the last 2 bytes of the question section (at questionEnd - 4).
            // We read it directly to avoid TYPE.fromValue() having to deal with UNKNOWN
            // inside DNSQuestion.fromByteArray (which would also need CLASS lookup).
            if (questionLength >= 4) {
                int qtypeValueOffset = questionEnd - 4; // 2 bytes QTYPE, then 2 bytes QCLASS
                int qtypeRaw = ((data[qtypeValueOffset] & 0xFF) << 8) | (data[qtypeValueOffset + 1] & 0xFF);
                TYPE qtype = TYPE.fromValue(qtypeRaw);
                if (qtype == TYPE.UNKNOWN) {
                    logger.debug("Unsupported DNS TYPE={} from client {} — responding NOTIMPL", qtypeRaw, clientInfo);
                    byte[] notImplBytes = DnsResponseFactory.createNotImplementedResponse(receivedHeader, questionBuffer);
                    DatagramPacket response = new DatagramPacket(
                            notImplBytes, notImplBytes.length, clientInfo.toSocketAddress());
                    synchronized (socket) {
                        socket.send(response);
                    }
                    return;
                }
            }

            DNSQuestion receivedQuestion = DNSQuestion.fromByteArray(questionBuffer);

            // Resolve via AdvancedDnsResolver
            DNSResourceRecord responseRecord = dnsResolver.resolve(receivedQuestion, clientIp);

            // Build binary response
            byte[] responseBytes;
            if (responseRecord == null || responseRecord.getRdLength() == 0) {
                boolean isBlocked = (responseRecord != null && responseRecord.getRdLength() == 0);
                responseBytes = DnsResponseFactory.createErrorOrBlockedResponse(receivedHeader, receivedQuestion, isBlocked);
            } else {
                responseBytes = DnsResponseFactory.createSuccessResponse(receivedHeader, receivedQuestion, responseRecord);
            }

            DatagramPacket responsePacket = new DatagramPacket(
                    responseBytes, responseBytes.length, clientInfo.toSocketAddress());
            synchronized (socket) {
                socket.send(responsePacket);
            }
        } catch (Exception e) {
            logger.error("Error processing DNS packet for client {}: {}", clientInfo, e.getMessage(), e);
        }
    }
}

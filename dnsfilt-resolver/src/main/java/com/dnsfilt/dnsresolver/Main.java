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
                final byte[] packetData  = requestPacket.getData();
                final java.net.SocketAddress transportPeer = requestPacket.getSocketAddress();

                // --- PROXY Protocol v2 detection ---
                final ProxyProtocolResult proxyResult = ProxyProtocolV2Decoder.decode(packetData, 0, receivedLength);

                // Detailed observability log for packet inspection
                logger.info(
                    "DNS packet received: length={}, proxyDetected={}, payloadOffset={}, payloadLength={}, client={}:{}",
                    receivedLength,
                    proxyResult != null && proxyResult.proxyDetected,
                    proxyResult != null ? proxyResult.payloadOffset : -1,
                    proxyResult != null ? proxyResult.payloadLength : -1,
                    proxyResult != null && proxyResult.sourceAddress != null
                        ? proxyResult.sourceAddress.getHostAddress()
                        : null,
                    proxyResult != null ? proxyResult.sourcePort : 0
                );

                if (proxyResult == null) {
                    logger.warn("Dropping malformed PROXY v2 packet, length={}", receivedLength);
                    continue;
                }

                // Resolve client identity (for logging, threat intelligence, and analytics)
                final DnsClientInfo clientInfo;
                if (proxyResult.proxyDetected && proxyResult.sourceAddress != null) {
                    clientInfo = new DnsClientInfo(proxyResult.sourceAddress, proxyResult.sourcePort);
                } else {
                    clientInfo = DnsClientInfo.fromSocketAddress(transportPeer);
                }

                final int dnsOffset = proxyResult.payloadOffset;
                final int dnsLength = proxyResult.payloadLength;

                // Dispatch to virtual thread — transportPeer receives the UDP response, clientInfo is for policy/metrics
                CompletableFuture.runAsync(
                        () -> processPacket(serverSocket, transportPeer, clientInfo, packetData, dnsOffset, dnsLength, dnsResolver),
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
     * Processes a single DNS datagram.
     *
     * @param socket        the server socket for sending the response
     * @param transportPeer the network socket address to send the UDP response back to (e.g. NGINX)
     * @param clientInfo    authentic edge client identity (from PROXY v2 or socket) used for metrics/policy
     * @param data          the full receive buffer
     * @param dnsOffset     byte offset into data where the DNS payload starts
     * @param dnsLength     number of DNS payload bytes
     * @param dnsResolver   the resolver to dispatch the question to
     */
    private static void processPacket(DatagramSocket socket, java.net.SocketAddress transportPeer,
                                      DnsClientInfo clientInfo, byte[] data, int dnsOffset, int dnsLength,
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

            // Guard: check for unknown/unsupported TYPE before parsing
            if (questionLength >= 4) {
                int qtypeValueOffset = questionEnd - 4;
                int qtypeRaw = ((data[qtypeValueOffset] & 0xFF) << 8) | (data[qtypeValueOffset + 1] & 0xFF);
                TYPE qtype = TYPE.fromValue(qtypeRaw);
                if (qtype == TYPE.UNKNOWN) {
                    logger.debug("Unsupported DNS TYPE={} from client {} — responding NOTIMPL", qtypeRaw, clientInfo);
                    byte[] notImplBytes = DnsResponseFactory.createNotImplementedResponse(receivedHeader, questionBuffer);
                    DatagramPacket response = new DatagramPacket(notImplBytes, notImplBytes.length, transportPeer);
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

            // CRITICAL: Always send the UDP response back to transportPeer (NGINX), NOT clientInfo!
            // NGINX handles relaying the response back to the client.
            DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, transportPeer);
            synchronized (socket) {
                socket.send(responsePacket);
            }
        } catch (Exception e) {
            logger.error("Error processing DNS packet for client {}: {}", clientInfo, e.getMessage(), e);
        }
    }
}

package com.dnsfilt.dnsresolver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
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

                byte[] packetData = Arrays.copyOf(requestPacket.getData(), requestPacket.getLength());
                SocketAddress clientAddress = requestPacket.getSocketAddress();

                // Dispatch UDP packet resolution using Virtual Threads
                CompletableFuture.runAsync(() -> processPacket(serverSocket, clientAddress, packetData, dnsResolver), virtualExecutor);
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

    private static void processPacket(DatagramSocket socket, SocketAddress clientAddress, byte[] data, AdvancedDnsResolver dnsResolver) {
        try {
            if (data.length < 12) {
                logger.warn("Received invalid DNS packet: length < 12 bytes.");
                return;
            }

            // Extract client IP string from SocketAddress
            String clientIp = clientAddress.toString().replaceAll("^/+", "").split(":")[0];

            // Parse Header & Question
            byte[] headerBuffer = Arrays.copyOfRange(data, 0, 12);
            DNSHeader receivedHeader = DNSHeader.fromByteArray(headerBuffer);

            int questionLength = DNSQuestion.getQuestionLength(data, 12);
            byte[] questionBuffer = Arrays.copyOfRange(data, 12, Math.min(12 + questionLength, data.length));
            DNSQuestion receivedQuestion = DNSQuestion.fromByteArray(questionBuffer);

            // Resolve Query via AdvancedDnsResolver
            DNSResourceRecord responseRecord = dnsResolver.resolve(receivedQuestion, clientIp);

            // Build binary response using DnsResponseFactory
            byte[] responseBytes;
            if (responseRecord == null || responseRecord.getRdLength() == 0) {
                boolean isBlocked = (responseRecord != null && responseRecord.getRdLength() == 0);
                responseBytes = DnsResponseFactory.createErrorOrBlockedResponse(receivedHeader, receivedQuestion, isBlocked);
            } else {
                responseBytes = DnsResponseFactory.createSuccessResponse(receivedHeader, receivedQuestion, responseRecord);
            }

            DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, clientAddress);
            synchronized (socket) {
                socket.send(responsePacket);
            }
        } catch (Exception e) {
            logger.error("Error processing DNS packet for client {}: {}", clientAddress, e.getMessage(), e);
        }
    }
}

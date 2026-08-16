package com.dnsfilt.dnsresolver.service;

import com.dnsfilt.dnsresolver.config.AppConfig;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * KafkaProducerService
 * 
 * Manages Kafka producer instances for 10-minute analytics batch streaming.
 * Implements the Bill Pugh Singleton Pattern.
 */
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private KafkaProducer<String, byte[]> byteProducer;
    private boolean isEnabled = false;

    private KafkaProducerService() {
        try {
            AppConfig config = AppConfig.getInstance();
            String bootstrapServers = config.getEnvVariable("kafka.bootstrap.servers");

            if (bootstrapServers == null || bootstrapServers.trim().isEmpty()
                    || "none".equalsIgnoreCase(bootstrapServers)) {
                logger.info("Kafka bootstrap.servers not configured or set to 'none'. Kafka logging disabled.");
                return;
            }

            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "1");
            props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

            String securityProtocol = config.getEnvVariable("kafka.security.protocol");
            String protocol = (securityProtocol != null && !securityProtocol.trim().isEmpty()) ? securityProtocol : "SASL_PLAINTEXT";
            props.put("security.protocol", protocol);

            String saslMechanism = config.getEnvVariable("kafka.sasl.mechanism");
            String saslUsername = config.getEnvVariable("kafka.sasl.username");
            String saslPassword = config.getEnvVariable("kafka.sasl.password");

            String mechanism = (saslMechanism != null && !saslMechanism.trim().isEmpty()) ? saslMechanism : "PLAIN";
            props.put("sasl.mechanism", mechanism);

            if (saslUsername != null && saslPassword != null && !saslUsername.trim().isEmpty()) {
                String jaasConfig = String.format(
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                        saslUsername, saslPassword);
                props.put("sasl.jaas.config", jaasConfig);
            }

            props.put("sasl.client.callback.handler.class", Java21SaslCallbackHandler.class.getName());

            // Only configure SSL truststore if SSL/SASL_SSL is explicitly chosen
            if ("SASL_SSL".equalsIgnoreCase(protocol) || "SSL".equalsIgnoreCase(protocol)) {
                String caCertPath = config.getEnvVariable("kafka.ssl.truststore.location");
                File pemFile = resolveCertFile(caCertPath);
                if (pemFile != null) {
                    logger.info("Building in-memory JKS truststore from CA certificate: {}", pemFile.getAbsolutePath());
                    File jksFile = buildJksTruststore(pemFile);
                    props.put("ssl.truststore.type", "JKS");
                    props.put("ssl.truststore.location", jksFile.getAbsolutePath());
                    props.put("ssl.truststore.password", "");
                    props.put("ssl.endpoint.identification.algorithm", "https");
                }
            }

            this.byteProducer = new KafkaProducer<>(props);
            this.isEnabled = true;
            logger.info("KafkaProducerService initialized successfully for broker: {} (protocol={})", bootstrapServers, protocol);

        } catch (Exception e) {
            logger.error("Failed to initialize KafkaProducer: {}. Kafka logging disabled.", e.getMessage(), e);
            this.isEnabled = false;
        }
    }

    /**
     * Bill Pugh Singleton Holder
     */
    private static class InstanceHolder {
        private static final KafkaProducerService INSTANCE = new KafkaProducerService();
    }

    public static KafkaProducerService getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public KafkaProducer<String, byte[]> getProducer() {
        return byteProducer;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    private static File buildJksTruststore(File pemFile) throws Exception {
        List<Certificate> certs = new ArrayList<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        String content = new String(Files.readAllBytes(pemFile.toPath()), StandardCharsets.UTF_8);
        String[] blocks = content.split("(?<=-----END CERTIFICATE-----)");
        for (String block : blocks) {
            block = block.trim();
            if (!block.isEmpty() && block.contains("BEGIN CERTIFICATE")) {
                certs.add(cf.generateCertificate(new ByteArrayInputStream(block.getBytes(StandardCharsets.UTF_8))));
            }
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, "".toCharArray());
        for (int i = 0; i < certs.size(); i++) {
            ks.setCertificateEntry("cert-" + i, certs.get(i));
        }

        File tempJks = File.createTempFile("kafka-truststore-", ".jks");
        tempJks.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempJks)) {
            ks.store(fos, "".toCharArray());
        }
        return tempJks;
    }

    private static File resolveCertFile(String caCertPath) {
        if (caCertPath == null || caCertPath.trim().isEmpty()) {
            caCertPath = "certs/ca.pem";
        }
        String[] candidatePaths = {
            caCertPath,
            new File(caCertPath).getAbsolutePath(),
            new File("dnsfilt-resolver/" + caCertPath).getAbsolutePath(),
            "/app/" + caCertPath,
            "/app/certs/ca.pem"
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    public void sendRawAnalyticsBatch(String topic, byte[] compressedPayload) {
        if (!isEnabled || byteProducer == null) return;
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, compressedPayload);
        byteProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Failed to send 10-min analytics batch to Kafka: {}", exception.getMessage());
            } else {
                logger.debug("Sent 10-min analytics batch to topic: {}, offset: {}", metadata.topic(), metadata.offset());
            }
        });
    }

    public void close() {
        if (byteProducer != null) {
            byteProducer.flush();
            byteProducer.close();
            logger.info("KafkaProducer closed.");
        }
    }
}

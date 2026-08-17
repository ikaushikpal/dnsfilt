package com.dnsfilt.dnsresolver.service;

import com.dnsfilt.dnsresolver.config.AppConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * KafkaProducerService
 * 
 * High-throughput asynchronous batch producer for dnsfilt-resolver.
 * Serializes DNS analytics events to Kafka for downstream aggregation by dnsfilt-analytics.
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

            // Verify DNS resolution of bootstrap host before initializing KafkaProducer
            String primary = bootstrapServers.split(",")[0].trim();
            String host = primary.contains(":") ? primary.split(":")[0] : primary;
            try {
                InetAddress.getByName(host);
            } catch (Exception ex) {
                logger.warn("Kafka bootstrap host '{}' is not resolvable via DNS. Kafka logging will remain disabled.", host);
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
            String saslMechanism = config.getEnvVariable("kafka.sasl.mechanism");
            String saslUsername = config.getEnvVariable("kafka.sasl.username");
            String saslPassword = config.getEnvVariable("kafka.sasl.password");

            String protocol;
            if (securityProtocol != null && !securityProtocol.trim().isEmpty()) {
                protocol = securityProtocol.trim();
            } else if (saslUsername != null && !saslUsername.trim().isEmpty()) {
                protocol = "SASL_PLAINTEXT";
            } else {
                protocol = "PLAINTEXT";
            }
            props.put("security.protocol", protocol);

            // Configure SASL JAAS authentication only when credentials are provided
            if (saslUsername != null && saslPassword != null && !saslUsername.trim().isEmpty() && !saslPassword.trim().isEmpty()) {
                String mechanism = (saslMechanism != null && !saslMechanism.trim().isEmpty()) ? saslMechanism : "PLAIN";
                props.put("sasl.mechanism", mechanism);

                String jaasConfig = String.format(
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                        saslUsername, saslPassword);
                props.put("sasl.jaas.config", jaasConfig);
                props.put("sasl.client.callback.handler.class", Java21SaslCallbackHandler.class.getName());
                logger.info("Configured Kafka SASL authentication ({}) with Java21SaslCallbackHandler for user: {}", mechanism, saslUsername);
            }

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
        return isEnabled && byteProducer != null;
    }

    public void close() {
        if (byteProducer != null) {
            try {
                byteProducer.flush();
                byteProducer.close();
                logger.info("KafkaProducerService closed successfully.");
            } catch (Exception e) {
                logger.warn("Error closing KafkaProducer: {}", e.getMessage());
            }
        }
    }

    private File resolveCertFile(String configuredPath) {
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            File f = new File(configuredPath.trim());
            if (f.exists()) return f;
        }
        File standardFile = new File("certs/ca.pem");
        if (standardFile.exists()) return standardFile;

        return null;
    }

    private File buildJksTruststore(File pemFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        try (InputStream in = new ByteArrayInputStream(Files.readAllBytes(pemFile.toPath()))) {
            int index = 0;
            while (in.available() > 0) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
                if (cert != null) {
                    ks.setCertificateEntry("ca-" + index++, cert);
                }
            }
        }

        File tempJks = File.createTempFile("kafka-truststore-", ".jks");
        tempJks.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tempJks)) {
            ks.store(out, "".toCharArray());
        }
        return tempJks;
    }
}

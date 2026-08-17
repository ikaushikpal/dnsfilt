package com.dnsfilt.dnsanalytics.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.io.File;
import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConsumerConfig
 * 
 * Configures the Spring Kafka ConsumerFactory with dynamic resilience for:
 * - Local / Docker host networking & unresolvable host fallback
 * - Java 21+ SASL JAAS authentication via Java21SaslCallbackHandler
 * - Non-fatal startup when topics are provisioned dynamically (missingTopicsFatal = false)
 * - Automatic background reconnection and exponential backoff
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${KAFKA_BOOTSTRAP_SERVERS:${kafka.bootstrap-servers:host.docker.internal:9092}}")
    private String bootstrapServers;

    @Value("${KAFKA_SECURITY_PROTOCOL:${kafka.security-protocol:}}")
    private String securityProtocol;

    @Value("${KAFKA_SASL_MECHANISM:${kafka.sasl.mechanism:PLAIN}}")
    private String saslMechanism;

    @Value("${KAFKA_SASL_USERNAME:${kafka.sasl.username:}}")
    private String saslUsername;

    @Value("${KAFKA_SASL_PASSWORD:${kafka.sasl.password:}}")
    private String saslPassword;

    @Value("${KAFKA_SSL_TRUSTSTORE_LOCATION:${kafka.ssl.truststore-location:}}")
    private String truststoreLocation;

    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        String validBootstrap = resolveBootstrapServers(bootstrapServers);
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, validBootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dnsfilt-analytics-rollup-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000);

        // Security Protocol (SASL_PLAINTEXT, SASL_SSL, PLAINTEXT)
        String protocol;
        if (securityProtocol != null && !securityProtocol.trim().isEmpty()) {
            protocol = securityProtocol.trim();
        } else if (saslUsername != null && !saslUsername.trim().isEmpty()) {
            protocol = "SASL_PLAINTEXT";
        } else {
            protocol = "PLAINTEXT";
        }
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol);
        logger.info("Kafka security.protocol set to: {}", protocol);

        // Configure SASL JAAS authentication with Java 21 callback handler
        if (saslUsername != null && !saslUsername.trim().isEmpty() &&
            saslPassword != null && !saslPassword.trim().isEmpty()) {
            String mechanism = (saslMechanism != null && !saslMechanism.trim().isEmpty()) ? saslMechanism : "PLAIN";
            props.put(SaslConfigs.SASL_MECHANISM, mechanism);

            String jaasConfig = String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    saslUsername, saslPassword);
            props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            
            // Critical for Java 21+: Bypasses deprecated/removed Subject.getSubject()
            props.put(SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS, Java21SaslCallbackHandler.class.getName());
            logger.info("Configured Kafka SASL authentication ({}) with Java21SaslCallbackHandler for user: {}", mechanism, saslUsername);
        }

        // Configure SSL Truststore if SASL_SSL is used
        if ("SASL_SSL".equalsIgnoreCase(protocol) || "SSL".equalsIgnoreCase(protocol)) {
            if (truststoreLocation != null && !truststoreLocation.trim().isEmpty()) {
                File certFile = new File(truststoreLocation.trim());
                if (certFile.exists()) {
                    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, certFile.getAbsolutePath());
                    logger.info("Configured Kafka SSL truststore: {}", certFile.getAbsolutePath());
                }
            }
        }

        logger.info("Initialized Kafka ConsumerFactory connecting to broker: {}", validBootstrap);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Critical: Do NOT crash Spring application context if Kafka topic or broker is initially warming up
        factory.getContainerProperties().setMissingTopicsFatal(false);
        factory.getContainerProperties().setAuthExceptionRetryInterval(Duration.ofSeconds(10));
        factory.getContainerProperties().setPollTimeout(3000L);
        
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(3000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }

    private String resolveBootstrapServers(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return "127.0.0.1:9092";
        }
        String servers = configured.trim();
        String primary = servers.split(",")[0].trim();
        String host = primary.contains(":") ? primary.split(":")[0] : primary;

        try {
            InetAddress.getByName(host);
            return servers;
        } catch (Exception e) {
            logger.warn("Kafka bootstrap host '{}' is not resolvable via DNS. Falling back to 127.0.0.1:9092 until DNS resolves.", host);
            return "127.0.0.1:9092";
        }
    }
}

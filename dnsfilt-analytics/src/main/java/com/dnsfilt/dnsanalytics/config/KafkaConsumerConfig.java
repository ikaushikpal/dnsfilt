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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConsumerConfig
 * 
 * Configures the Spring Kafka ConsumerFactory with dynamic support for:
 * - Local / Docker host networking (host.docker.internal:9092)
 * - SASL_PLAINTEXT authentication
 * - SASL_SSL authentication with CA truststore
 * - Binary ByteArray deserialization for Zstd Protobuf payloads
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${KAFKA_BOOTSTRAP_SERVERS:${kafka.bootstrap-servers:host.docker.internal:9092}}")
    private String bootstrapServers;

    @Value("${KAFKA_SECURITY_PROTOCOL:${kafka.security-protocol:PLAINTEXT}}")
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

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dnsfilt-analytics-rollup-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        // Configure Security Protocol (SASL_PLAINTEXT, SASL_SSL, PLAINTEXT)
        if (securityProtocol != null && !securityProtocol.trim().isEmpty()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol.trim());
            logger.info("Kafka security.protocol set to: {}", securityProtocol);
        }

        // Configure SASL JAAS authentication
        if (saslUsername != null && !saslUsername.trim().isEmpty() &&
            saslPassword != null && !saslPassword.trim().isEmpty()) {
            String mechanism = (saslMechanism != null && !saslMechanism.trim().isEmpty()) ? saslMechanism : "PLAIN";
            props.put(SaslConfigs.SASL_MECHANISM, mechanism);

            String jaasConfig = String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    saslUsername, saslPassword);
            props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            logger.info("Configured Kafka SASL authentication for user: {}", saslUsername);
        }

        // Configure SSL Truststore if SASL_SSL is used
        if ("SASL_SSL".equalsIgnoreCase(securityProtocol) || "SSL".equalsIgnoreCase(securityProtocol)) {
            if (truststoreLocation != null && !truststoreLocation.trim().isEmpty()) {
                File certFile = new File(truststoreLocation.trim());
                if (certFile.exists()) {
                    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, certFile.getAbsolutePath());
                    logger.info("Configured Kafka SSL truststore: {}", certFile.getAbsolutePath());
                }
            }
        }

        logger.info("Initialized Kafka ConsumerFactory connecting to broker: {}", bootstrapServers);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}

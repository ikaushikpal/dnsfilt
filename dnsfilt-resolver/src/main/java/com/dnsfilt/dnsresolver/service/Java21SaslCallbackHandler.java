package com.dnsfilt.dnsresolver.service;

import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.util.List;
import java.util.Map;

/**
 * Custom SASL callback handler that reads credentials directly from the JAAS config.
 * This is required because kafka-clients' default SaslClientCallbackHandler calls
 * Subject.getSubject(AccessControlContext) which throws UnsupportedOperationException
 * on Java 21+ (the API was removed). This handler bypasses that entirely.
 */
public class Java21SaslCallbackHandler implements AuthenticateCallbackHandler {
    private static final Logger logger = LoggerFactory.getLogger(Java21SaslCallbackHandler.class);

    private String username;
    private char[] password;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism,
                          List<AppConfigurationEntry> jaasConfigEntries) {
        for (AppConfigurationEntry entry : jaasConfigEntries) {
            Object u = entry.getOptions().get("username");
            Object p = entry.getOptions().get("password");
            if (u != null) username = u.toString();
            if (p != null) password = p.toString().toCharArray();
        }
        logger.debug("Java21SaslCallbackHandler configured for mechanism, user={}", username);
    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback nc) {
                nc.setName(username != null ? username : nc.getDefaultName());
            } else if (callback instanceof PasswordCallback pc) {
                pc.setPassword(password != null ? password : new char[0]);
            } else {
                throw new UnsupportedCallbackException(callback,
                        "Unrecognised callback type: " + callback.getClass().getName());
            }
        }
    }

    @Override
    public void close() {
        // wipe password from memory
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}

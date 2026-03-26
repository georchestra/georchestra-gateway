package org.georchestra.gateway.security.oauth2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates that disableUidPrefix is not enabled with multiple OAuth2
 * providers. Fails fast at startup if misconfigured.
 */
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class Oauth2UidPrefixValidator {

    @Value("${georchestra.gateway.security.disableUidPrefix:false}")
    private boolean disableUidPrefix;

    @Value("${georchestra.gateway.security.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    private final Environment environment;

    public Oauth2UidPrefixValidator(Environment environment) {
        this.environment = environment;
        log.info("Oauth2UidPrefixValidator instantiated");
    }

    @PostConstruct
    public void validate() {
        if (!oauth2Enabled || !disableUidPrefix) {
            return;
        }

        Set<String> registrationIds = extractOAuth2RegistrationIds();

        if (registrationIds.size() > 1) {
            throw new IllegalStateException("Invalid configuration: georchestra.gateway.security.disableUidPrefix=true "
                    + "cannot be used with multiple OAuth2 providers. Found " + registrationIds.size()
                    + " provider(s): " + registrationIds + ". "
                    + "This configuration would cause UID collisions between providers. "
                    + "Either set disableUidPrefix=false or configure only a single OAuth2 provider.");
        }
    }

    /**
     * Extracts all OAuth2 registration IDs from spring.security.oauth2.client.registration.*
     */
    private Set<String> extractOAuth2RegistrationIds() {
        Set<String> registrationIds = new HashSet<>();

        if (!(environment instanceof AbstractEnvironment abstractEnv)) {
            log.warn("Cannot validate OAuth2 configuration: Environment is not AbstractEnvironment");
            return registrationIds;
        }

        abstractEnv.getPropertySources().stream()
            .filter(ps -> ps instanceof EnumerablePropertySource)
            .flatMap(ps -> java.util.Arrays.stream(((EnumerablePropertySource<?>) ps).getPropertyNames()))
            .filter(name -> name.startsWith("spring.security.oauth2.client.registration."))
            .map(name -> {
                // Extract provider ID from: spring.security.oauth2.client.registration.<PROVIDER_ID>.*
                String[] parts = name.split("\\.");
                if (parts.length >= 6) {
                    return parts[5]; // <PROVIDER_ID>
                }
                return null;
            })
            .filter(id -> id != null)
            .forEach(registrationIds::add);

        return registrationIds;
    }
}
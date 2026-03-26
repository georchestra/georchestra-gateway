package org.georchestra.gateway.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class Oauth2UidPrefixValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldFailWhenDisableUidPrefixIsTrueAndMultipleOauth2ProvidersAreConfigured() {
        contextRunner
                .withPropertyValues("georchestra.gateway.security.disableUidPrefix=true",
                        "georchestra.gateway.security.oauth2.enabled=true",
                        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
                        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
                        "spring.security.oauth2.client.registration.github.client-id=test-github-client",
                        "spring.security.oauth2.client.registration.github.client-secret=test-github-secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause().hasMessageContaining("cannot be used with multiple OAuth2 providers");
                });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        Oauth2UidPrefixValidator oauth2UidPrefixValidator(Environment environment) {
            return new Oauth2UidPrefixValidator(environment);
        }
    }
}
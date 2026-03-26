package org.georchestra.gateway.app;

import org.georchestra.gateway.security.oauth2.Oauth2UidPrefixValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class GatewayValidationConfiguration {

    @Bean
    Oauth2UidPrefixValidator oauth2UidPrefixValidator(Environment environment) {
        return new Oauth2UidPrefixValidator(environment);
    }
}
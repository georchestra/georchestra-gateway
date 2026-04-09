/*
 * Copyright (C) 2023 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.gateway.accounts.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Test class for {@link AccountCreatedEventsConfigurationProperties}. Verifies
 * that configuration properties are properly bound and validated.
 */
class AccountCreatedEventsConfigurationPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AccountCreatedEventsConfigurationProperties.class)
    static class TestConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void testDefaultValues() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AccountCreatedEventsConfigurationProperties props = context
                    .getBean(AccountCreatedEventsConfigurationProperties.class);
            assertThat(props.getUrl()).isNull();
        });
    }

    @Test
    void testUrlBinding() {
        runner.withPropertyValues(
                "georchestra.gateway.security.events.accountcreated.url=http://localhost:9090/api/events")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AccountCreatedEventsConfigurationProperties props = context
                            .getBean(AccountCreatedEventsConfigurationProperties.class);
                    assertThat(props.getUrl()).isEqualTo("http://localhost:9090/api/events");
                });
    }

    @Test
    void testUrlBindingWithHttps() {
        runner.withPropertyValues(
                "georchestra.gateway.security.events.accountcreated.url=https://events.example.com/webhook")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AccountCreatedEventsConfigurationProperties props = context
                            .getBean(AccountCreatedEventsConfigurationProperties.class);
                    assertThat(props.getUrl()).isEqualTo("https://events.example.com/webhook");
                });
    }

    @Test
    void testConstantValues() {
        assertThat(AccountCreatedEventsConfigurationProperties.PREFIX)
                .isEqualTo("georchestra.gateway.security.events.accountcreated");
        assertThat(AccountCreatedEventsConfigurationProperties.PROPERTY_EXISTS)
                .isEqualTo("georchestra.gateway.security.events.accountcreated.url");
    }

    @Test
    void testSetterAndGetter() {
        AccountCreatedEventsConfigurationProperties props = new AccountCreatedEventsConfigurationProperties();
        props.setUrl("http://test.com");
        assertThat(props.getUrl()).isEqualTo("http://test.com");
    }
}

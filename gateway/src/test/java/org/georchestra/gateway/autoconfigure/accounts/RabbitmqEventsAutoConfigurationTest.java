package org.georchestra.gateway.autoconfigure.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.georchestra.gateway.accounts.events.rabbitmq.RabbitmqAccountCreatedEventSender;
import org.georchestra.gateway.accounts.events.rabbitmq.RabbitmqEventsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.actuate.amqp.RabbitHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Application context test for {@link RabbitmqEventsConfiguration}
 */
class RabbitmqEventsAutoConfigurationTest {

    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitmqEventsAutoConfiguration.class));

    @Test
    void conditionalOnPropertyNotSet() {
        runner.run(
                context -> assertThat(context).hasNotFailed().doesNotHaveBean(RabbitmqAccountCreatedEventSender.class));
    }

    @Test
    void conditionalOnPropertyDisabled() {
        runner.withPropertyValues("georchestra.gateway.security.events.rabbitmq.enabled=false").run(
                context -> assertThat(context).hasNotFailed().doesNotHaveBean(RabbitmqAccountCreatedEventSender.class));
    }

    @Test
    void conditionalOnPropertyEnabled_requires_default_ldap_and_create_users_enabled() {
        runner.withPropertyValues("georchestra.gateway.security.createNonExistingUsersInLDAP=false", //
                "georchestra.gateway.security.ldap.default.enabled=", //
                "georchestra.gateway.security.events.rabbitmq.enabled=true", //
                "georchestra.gateway.security.events.rabbitmq.host=test.rabbit", //
                "georchestra.gateway.security.events.rabbitmq.port=3333", //
                "georchestra.gateway.security.events.rabbitmq.user=bunny", //
                "georchestra.gateway.security.events.rabbitmq.password=rabbit"//
        ).run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(RabbitmqAccountCreatedEventSender.class));

        runner.withPropertyValues("georchestra.gateway.security.createNonExistingUsersInLDAP=true", //
                "georchestra.gateway.security.ldap.default.enabled=true", //
                "georchestra.gateway.security.events.rabbitmq.enabled=true", //
                "georchestra.gateway.security.events.rabbitmq.host=test.rabbit", //
                "georchestra.gateway.security.events.rabbitmq.port=3333", //
                "georchestra.gateway.security.events.rabbitmq.user=bunny", //
                "georchestra.gateway.security.events.rabbitmq.password=rabbit"//
        ).run(context -> {

            assertThat(context).hasNotFailed().hasSingleBean(RabbitmqAccountCreatedEventSender.class);

            assertThat(context).hasBean("connectionFactory");
            CachingConnectionFactory rabbitMQConnectionFactory = (CachingConnectionFactory) context
                    .getBean("connectionFactory");
            assertThat(rabbitMQConnectionFactory.getHost()).isEqualTo("test.rabbit");
            assertThat(rabbitMQConnectionFactory.getPort()).isEqualTo(3333);
            assertThat(rabbitMQConnectionFactory.getUsername()).isEqualTo("bunny");

            assertThat(context).hasBean("rabbitHealthIndicator");
            assertThat(context.getBean("rabbitHealthIndicator")).isInstanceOf(RabbitHealthIndicator.class);

        });
    }
}

package org.georchestra.gateway.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.geonetwork.testcontainers.postgres.GeorchestraDatabaseContainer;
import org.georchestra.gateway.accounts.admin.AccountCreated;
import org.georchestra.gateway.accounts.events.rabbitmq.RabbitmqAccountCreatedEventSender;
import org.georchestra.gateway.accounts.events.rabbitmq.RabbitmqEventsListener;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.testcontainers.console.GeorchestraConsoleContainer;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Integration tests for {@link RabbitmqAccountCreatedEventSender}.
 */
@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@ActiveProfiles("rabbitmq")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = { "enableRabbitmqEvents=true", //
        "georchestra.datadir=src/test/resources/test-datadir"//
})
@Disabled("issue with rabbitMq console-side, see #143")
public class SendMessageRabbitmqIT {

    private @Autowired ApplicationEventPublisher eventPublisher;
    private @Autowired RabbitmqAccountCreatedEventSender sender;

    private static final int SMTPPORT = 25;

    public static final GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();
    public static final GeorchestraDatabaseContainer db = new GeorchestraDatabaseContainer();
    public static final GeorchestraConsoleContainer console = new GeorchestraConsoleContainer();

    public static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12"));

    @SuppressWarnings("resource") // closed at #shutDownContainers()
    public static GenericContainer<?> smtp = new GenericContainer<>("camptocamp/smtp-sink:latest")
            .withExposedPorts(SMTPPORT);

    public static @BeforeAll void startUpContainers() {
        db.start();
        ldap.start();
        smtp.start();
        rabbitmq.start();

        Testcontainers.exposeHostPorts(ldap.getMappedLdapPort(), db.getMappedDatabasePort(), rabbitmq.getAmqpPort(),
                smtp.getMappedPort(SMTPPORT));
        System.setProperty("georchestra.gateway.security.events.rabbitmq.host", "localhost");
        System.setProperty("georchestra.gateway.security.events.rabbitmq.port", String.valueOf(rabbitmq.getAmqpPort()));

        console.withCopyFileToContainer(MountableFile.forClasspathResource("test-datadir"), "/etc/georchestra")//
                .withEnv("enableRabbitmqEvents", "true").withEnv("pgsqlHost", "host.testcontainers.internal")//
                .withEnv("pgsqlPort", String.valueOf(db.getMappedDatabasePort()))//
                .withEnv("ldapHost", "host.testcontainers.internal")//
                .withEnv("ldapPort", String.valueOf(ldap.getMappedLdapPort()))//
                .withEnv("rabbitmqHost", "host.testcontainers.internal")//
                .withEnv("rabbitmqPort", String.valueOf(rabbitmq.getAmqpPort()))//
                .withEnv("rabbitmqUser", "guest")//
                .withEnv("rabbitmqPassword", "guest")//
                .withEnv("smtpHost", "host.testcontainers.internal")//
                .withEnv("smtpPort", String.valueOf(smtp.getMappedPort(SMTPPORT))).withLogToStdOut();

        console.start();
        System.setProperty("georchestra.console.url", "http://localhost:%d".formatted(console.getMappedConsolePort()));
    }

    public static @AfterAll void shutDownContainers() {
        console.stop();
        ldap.stop();
        db.stop();
        smtp.stop();
    }

    @Test
    void testReceivingMessageFromConsole(CapturedOutput output) {
        assertNotNull(sender);
        GeorchestraUser user = new GeorchestraUser();
        user.setId(UUID.randomUUID().toString());
        user.setLastUpdated("anystringwoulddo");
        user.setUsername("testamqp");
        user.setEmail("testamqp@georchestra.org");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setRoles(List.of("ADMINISTRATOR", "GN_ADMIN"));
        user.setTelephoneNumber("341444111");
        user.setTitle("developer");
        user.setNotes("user notes");
        user.setPostalAddress("123 java street");
        user.setOrganization("PSC");
        user.setOAuth2Provider("testProvider");
        user.setOAuth2Uid("123");
        eventPublisher.publishEvent(new AccountCreated(user));
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            return (RabbitmqEventsListener.getSynReceivedMessageUid().size() > 0);
        });
    }

}

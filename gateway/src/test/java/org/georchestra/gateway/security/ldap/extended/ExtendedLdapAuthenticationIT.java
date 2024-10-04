package org.georchestra.gateway.security.ldap.extended;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.filter.headers.providers.JsonPayloadHeadersContributor;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@ActiveProfiles({ "createaccount" })
@AutoConfigureWebTestClient(timeout = "PT20S")
public class ExtendedLdapAuthenticationIT {
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    private @Autowired WebTestClient testClient;

    public static @BeforeAll void startUpContainers() {
        ldap.start();
    }

    public static @AfterAll void shutDownContainers() {
        ldap.stop();
    }

    @DynamicPropertySource
    static void registerLdap(DynamicPropertyRegistry registry) {
        registry.add("testcontainers.georchestra.ldap.host", () -> "127.0.0.1");
        registry.add("testcontainers.georchestra.ldap.port", ldap::getMappedLdapPort);
    }

    public @Test void testWhoami() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser.username").isEqualTo("testadmin");
    }

    public @Test void testWhoamiUsingEmail() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic cHNjK3Rlc3RhZG1pbkBnZW9yY2hlc3RyYS5vcmc6dGVzdGFkbWlu") // psc+testadmin@georchestra.org:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser.username").isEqualTo("testadmin");
    }

    public @Test void testWhoamiNoPasswordRevealed() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(
                        "$.['org.georchestra.gateway.security.ldap.extended.GeorchestraUserNamePasswordAuthenticationToken'].principal.password")
                .isEmpty();
    }

}

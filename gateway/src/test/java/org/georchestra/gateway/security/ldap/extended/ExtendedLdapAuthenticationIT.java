package org.georchestra.gateway.security.ldap.extended;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.DockerClientFactory;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@ActiveProfiles({ "createaccount" })
@AutoConfigureWebTestClient(timeout = "PT20S")
public class ExtendedLdapAuthenticationIT {
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    private @Autowired WebTestClient testClient;

    public static @BeforeAll void startUpContainers() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
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

    @Test
    void testWhoami() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser.username").isEqualTo("testadmin");
    }

    @Test
    void testWhoamiUsingEmail() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic cHNjK3Rlc3RhZG1pbkBnZW9yY2hlc3RyYS5vcmc6dGVzdGFkbWlu") // psc+testadmin@georchestra.org:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser.username").isEqualTo("testadmin");
    }

    @Test
    void testWhoamiNoPasswordRevealed() {
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

    public @Test void testWhoamiNoNotesRevealed() {
        testClient.get().uri("/whoami")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser.notes").isEmpty();
    }

    public @Test void testWhoamiNoAuth() {
        testClient.get().uri("/whoami")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isEmpty();
    }

}

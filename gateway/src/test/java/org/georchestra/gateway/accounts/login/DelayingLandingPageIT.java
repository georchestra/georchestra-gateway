package org.georchestra.gateway.accounts.login;

import org.georchestra.gateway.accounts.admin.CreateAccountUserCustomizer;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.security.oauth2.OAuth2RedirectAuthenticationSuccessHandler;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.DockerClientFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

/**
 * Integration tests for {@link CreateAccountUserCustomizer}.
 */
@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles("delaylandingpage")
public class DelayingLandingPageIT {
    private @Autowired WebTestClient testClient;
    private @Autowired OAuth2RedirectAuthenticationSuccessHandler oauth2RedirectAuthenticationSuccessHandler;

    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    public static @BeforeAll void startUpContainers() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        ldap.start();
    }

    public static @AfterAll void shutDownContainers() {
        ldap.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // Info to connect to the LDAP provided by testcontainers
        registry.add("ldapHost", () -> "127.0.0.1");
        registry.add("ldapPort", ldap::getMappedLdapPort);
    }

    @Test
    void verifyLoginRedirectToDelayingLandingPage() {
        testClient.mutateWith(csrf()) // If CSRF is enabled
                .post().uri("/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("username", "testadmin").with("password", "testadmin")).exchange()
                .expectStatus().is3xxRedirection().expectHeader().valueEquals("Location", "/success");
    }

    @Test
    void verifyOauth2RedirectAuthenticationSuccessHandler() {
        assertThat(oauth2RedirectAuthenticationSuccessHandler).isNotNull();
        assertThat(oauth2RedirectAuthenticationSuccessHandler.getDelegate())
                .isInstanceOf(RedirectServerAuthenticationSuccessHandler.class);

        RedirectServerAuthenticationSuccessHandler toCheck = (RedirectServerAuthenticationSuccessHandler) oauth2RedirectAuthenticationSuccessHandler
                .getDelegate();
        URI internalUri = (URI) ReflectionTestUtils.getField(toCheck, "location");

        assertThat(internalUri.toString()).isEqualTo("/success");
    }
}

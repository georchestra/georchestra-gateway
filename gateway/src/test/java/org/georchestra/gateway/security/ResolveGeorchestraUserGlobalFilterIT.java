package org.georchestra.gateway.security;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Optional;

import org.georchestra.gateway.accounts.admin.CreateAccountUserCustomizer;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.filter.headers.providers.JsonPayloadHeadersContributor;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.security.preauth.PreauthAuthenticationManager;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles("georheaders")
public class ResolveGeorchestraUserGlobalFilterIT {

    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    private @Autowired WebTestClient testClient;

    private @Autowired GatewayConfigProperties gatewayConfig;

    private @Autowired ApplicationContext context;

    @SuppressWarnings("rawtypes")
    public static GenericContainer<?> httpEcho = new GenericContainer(DockerImageName.parse("ealen/echo-server")) {
        @Override
        protected void doStart() {
            super.doStart();
            Integer mappedPort = this.getMappedPort(80);
            System.setProperty("httpEchoHost", this.getHost());
            System.setProperty("httpEchoPort", mappedPort.toString());
            System.out.println("Automatically set system property httpEchoHost=" + this.getHost());
            System.out.println("Automatically set system property httpEchoPort=" + mappedPort);
        }
    };

    public static @BeforeAll void startUpContainers() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        httpEcho.setExposedPorts(List.of(80));
        httpEcho.start();
        ldap.start();
    }

    public static @AfterAll void shutDownContainers() {
        ldap.stop();
        httpEcho.stop();
    }

    @Test
    void testReceivedHeadersAsJson() {
        gatewayConfig.getDefaultHeaders().setJsonUser(Optional.of(true));
        gatewayConfig.getDefaultHeaders().setJsonOrganization(Optional.of(true));
        assertNotNull(context.getBean(JsonPayloadHeadersContributor.class));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-user").exists().jsonPath(".request.headers.sec-organization").exists();
    }

    @Test
    void testJsonUserNoOrganization() {
        gatewayConfig.getDefaultHeaders().setJsonUser(Optional.of(true));
        gatewayConfig.getDefaultHeaders().setJsonOrganization(Optional.of(false));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-user").exists()//
                .jsonPath(".request.headers.sec-organization").doesNotHaveJsonPath();

    }

    @Test
    void testNoJsonUserJsonOrganization() {
        gatewayConfig.getDefaultHeaders().setJsonUser(Optional.of(false));
        gatewayConfig.getDefaultHeaders().setJsonOrganization(Optional.of(true));

        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-user").doesNotHaveJsonPath()//
                .jsonPath(".request.headers.sec-organization").exists();
    }

    @Test
    void testSecOrgnamePresent() {
        testClient.get().uri("/echo/")//
                .header("Host", "localhost")//
                .header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath(".request.headers.sec-orgname").exists();
    }

    /**
     * Show error message to OAuth2 user when a matching local account already
     * exists: i.e. it tries to create a user with an email address for which a user
     * already exists.
     * <p>
     * {@link GeorchestraUserMapper} calls the
     * {@link GeorchestraUserCustomizerExtension}s.
     * {@link CreateAccountUserCustomizer} will try to create an account with email
     * {@literal psc+testadmin@georchestra.org}, which already exists (for user
     * {@literal testadmin})
     */
    @Test
    void testRedirectIfOauth2UserExists() {
        final String email = "psc+testadmin@georchestra.org";
        final String expected = "/login?error=duplicate_account";

        testClient.get().uri("/echo/")//
                .header(PreauthAuthenticationManager.PREAUTH_HEADER_NAME, "true")
                .header(PreauthAuthenticationManager.PREAUTH_EMAIL, email)
                .header(PreauthAuthenticationManager.PREAUTH_FIRSTNAME, "bob")
                .header(PreauthAuthenticationManager.PREAUTH_LASTNAME, "sponge")
                .header(PreauthAuthenticationManager.PREAUTH_USERNAME, "bobsponge").accept(MediaType.APPLICATION_JSON)
                .exchange()//
                .expectStatus()//
                .is3xxRedirection().expectHeader().location(expected);
    }

}

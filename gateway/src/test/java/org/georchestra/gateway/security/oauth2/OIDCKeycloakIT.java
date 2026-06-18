package org.georchestra.gateway.security.oauth2;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = GeorchestraGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles("keycloak")
public class OIDCKeycloakIT {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
            .withRealmImportFile("/georchestra-oidc.json");

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AccountDao accountDao;

    // The previous webTestClient object will be scoped to our spring boot application
    // and won't allow reaching other endpoints (e.g. our keycloak). Hence using a
    // dedicated webclient for such calls.
    private final WebTestClient oidcClient = WebTestClient.bindToServer().build();

    // Using a traditional random port won't allow to use it in the dynamic property source
    // so we have to select the random port by ourselves.
    private static final int APP_PORT;

    // finds a free port on which spring could listen to.
    static {
        try (ServerSocket socket = new ServerSocket(0)) {
            APP_PORT = socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to allocate an open port", e);
        }
    }
    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {

        registry.add("server.port", () -> APP_PORT);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> "georchestra-oidc");
        registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> "ouMohlei4Tuthi6paimahr2ieRohvogh");
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> keycloak.getAuthServerUrl() + "/realms/georchestra-oidc");
        registry.add("spring.security.oauth2.client.registration.keycloak.redirect-uri", () -> "http://localhost:" + APP_PORT + "/login/oauth2/code/keycloak");
        // the email scope is mandatory, without it we cannot create an account into the LDAP.
        registry.add("spring.security.oauth2.client.registration.keycloak.scope", () -> "openid,profile,email,groups");

        registry.add("ldapHost", ldap::getHost);
        registry.add("ldapPort", () -> ldap.getMappedPort(389));
        registry.add("ldapScheme", () -> "ldap");
    }

    @BeforeAll
    public static void createAssetsInKeycloak() {
        createGroup("ROLE_USER");
        createGroup("GRP_AWESOME_ORG");
        createTestUser(List.of("ROLE_USER", "GRP_AWESOME_ORG"));
    }

    @Test
    public void testOidc() throws DataServiceException {
        FluxExchangeResult<Void> springSecurityInitialRedirect = webTestClient.get().uri("/oauth2/authorization/keycloak")
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class);
        URI springSecurityRedirect = springSecurityInitialRedirect.getResponseHeaders().getLocation();
        String cookie = springSecurityInitialRedirect.getResponseCookies().getFirst("SESSION").getValue();

        EntityExchangeResult<String> loginPageResult = oidcClient.get().uri(springSecurityRedirect)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String authSessionId = loginPageResult.getResponseCookies().getFirst("AUTH_SESSION_ID").getValue();
        String formActionUrl = extractFormAction(loginPageResult.getResponseBody());

        URI appCallbackUri = oidcClient.post().uri(formActionUrl)
                .cookie("AUTH_SESSION_ID", authSessionId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("username", "testoidcuser")
                        .with("password", "testoidcuser")
                        .with("credentialId", "")
                )
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class)
                .getResponseHeaders().getLocation();
        // Ensure we are being redirected back to the Spring Boot application callback
        assertThat(appCallbackUri.getPath()).contains("/login/oauth2/code/");

        FluxExchangeResult<Void> finalCallbackResult = webTestClient.get().uri(appCallbackUri)
                .cookie("SESSION", cookie)
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class);
        String sessionId = finalCallbackResult.getResponseCookies().getFirst("SESSION").getValue();

        // Access the secured resource using the established Spring Session
        webTestClient.get().uri("/whoami")
                .cookie("SESSION", sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.GeorchestraUser.username").isEqualTo("keycloak_testoidcuser");

        assertNotNull(accountDao.findByUID("keycloak_testoidcuser"),
                "Account should have been created in LDAP by CreateAccountUserCustomizer");
    }

    private String extractFormAction(String html) {
        // Regex to find: <form ... action="URL" ...>
        Pattern pattern = Pattern.compile("<form[^>]*action=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).replace("&amp;", "&");
        }
        throw new IllegalStateException("Could not find login form action in Keycloak HTML");
    }

    private static void createGroup(String name) {
        RealmResource realm = keycloak.getKeycloakAdminClient().realm("georchestra-oidc");
        GroupRepresentation grp = new GroupRepresentation();
        grp.setName(name);
        Response resp  = realm.groups().add(grp);
        resp.close();
    }

    private static void createTestUser(List<String> groups) {
        RealmResource realm = keycloak.getKeycloakAdminClient().realm("georchestra-oidc");
        UserRepresentation testuser = new UserRepresentation();
        testuser.setUsername("testoidcuser");
        testuser.setEmail("psc+testoidcuser@georchestra.org");
        testuser.setFirstName("test");
        testuser.setLastName("user");
        testuser.setGroups(groups);
        testuser.setEnabled(true);
        CredentialRepresentation pwd = new CredentialRepresentation();
        pwd.setTemporary(false);
        pwd.setType(CredentialRepresentation.PASSWORD);
        pwd.setValue("testoidcuser");
        testuser.setCredentials(List.of(pwd));

        Response response = realm.users().create(testuser);
        response.close();
    }
}

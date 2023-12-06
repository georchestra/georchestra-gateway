package org.georchestra.gateway.security.preauth;

import lombok.extern.slf4j.Slf4j;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles({ "preauth", "preauth-with-testcontainer-ldap", "echoservice" })
@Slf4j
public class PreauthAccessRuleCustomizerIT {

    private @Autowired WebTestClient testClient;

    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    public static GenericContainer httpEcho = new GenericContainer(DockerImageName.parse("ealen/echo-server")) {
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
        httpEcho.setExposedPorts(Arrays.asList(new Integer[] { 80 }));
        httpEcho.start();
        ldap.start();
    }

    public static @AfterAll void shutDownContainers() {
        ldap.stop();
        httpEcho.stop();
    }

    public @Test void testAdminAccess_NoAuthoritiesButAdminGeorchestraRoleFromLdap() {
        // The intent of this test is that even if the authorities are not populated in
        // case of a PreAuthenticatedAuthenticationToken
        // being used, we can still access a protected resource (e.g. reserved to an
        // admin) because the gateway
        // will make use of the resolved roles onto the GeorchestraUser object.
        testClient.get().uri("/whoami")//
                .header("sec-georchestra-preauthenticated", "true")//
                .header("preauth-username", "testadmin")//
                .header("preauth-email", "psc+testadmin@georchestra.org")//
                .header("preauth-firstname", "Admin")//
                .header("preauth-lastname", "Test")//
                .header("preauth-org", "GEORCHESTRA")//
                .exchange()//
                .expectBody().jsonPath(".GeorchestraUser.roles").isNotEmpty()//
                .jsonPath(
                        ".'org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken'.authorities")
                .isEmpty();

        testClient.get().uri("/echo/administrator")//
                .header("sec-georchestra-preauthenticated", "true")//
                .header("preauth-username", "testadmin")//
                .header("preauth-email", "psc+testadmin@georchestra.org")//
                .header("preauth-firstname", "Admin")//
                .header("preauth-lastname", "Test")//
                .header("preauth-org", "GEORCHESTRA").exchange()//
                .expectStatus()//
                .is2xxSuccessful();
    }

    public @Test void testAuthenticatedAccess_NoExplicitGroupNeeded() {
        testClient.get().uri("/echo/connected")//
                .header("sec-georchestra-preauthenticated", "true")//
                .header("preauth-username", "testuser")//
                .header("preauth-email", "psc+testuser@georchestra.org")//
                .header("preauth-firstname", "User")//
                .header("preauth-lastname", "Test")//
                .header("preauth-org", "GEORCHESTRA").exchange()//
                .expectStatus()//
                .is2xxSuccessful();
    }

    public @Test void testAnonymousAccess_BeingConnectedAsAdmin() {
        testClient.get().uri("/echo/anonymous")//
                .header("sec-georchestra-preauthenticated", "true")//
                .header("preauth-username", "testadmin")//
                .header("preauth-email", "psc+testadmin@georchestra.org")//
                .header("preauth-firstname", "Admin")//
                .header("preauth-lastname", "Test")//
                .header("preauth-org", "GEORCHESTRA").exchange()//
                .expectStatus()//
                .is2xxSuccessful();
    }

}

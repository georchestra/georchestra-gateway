package org.georchestra.gateway.accounts.admin;

import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GeorchestraGatewayApplication.class)
@AutoConfigureWebTestClient(timeout = "PT20S")
@ActiveProfiles({ "createaccount", "preauthbase64encoded" })
@Testcontainers(disabledWithoutDocker = true)
class PreauthHttpHeadersBase64EncodedCreateAccountIT {

    private @Autowired WebTestClient testClient;

    private @Autowired AccountDao accountDao;

    @Container
    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    @DynamicPropertySource
    static void registerLdap(DynamicPropertyRegistry registry) {
        registry.add("testcontainers.georchestra.ldap.host", () -> "127.0.0.1");
        registry.add("testcontainers.georchestra.ldap.port", ldap::getMappedLdapPort);
    }

    @Test
    void testPreauthenticatedHeaders_AccentedChars() throws Exception {
        testClient.get().uri("/whoami")//
                .header("sec-georchestra-preauthenticated", "true")//
                .header("preauth-username", "{base64}ZnZhbmRlcmJsYWg=")//
                .header("preauth-email", "{base64}ZnZhbmRlcmJsYWhAZ2VvcmNoZXN0cmEub3Jn")//
                .header("preauth-firstname", "{base64}RnJhbsOnb2lz")//
                .header("preauth-lastname", "{base64}VmFuIERlciBBY2NlbnTDqWQgQ2jDoHJhY3TDqHJz")//
                .header("preauth-org", "{base64}R0VPUkNIRVNUUkE=")//
                .exchange()//
                .expectStatus()//
                .is2xxSuccessful()//
                .expectBody()//
                .jsonPath("$.GeorchestraUser").isNotEmpty();

        // Make sure the account has been created and the strings have been correctly
        // evaluated at creation
        Account created = accountDao.findByUID("fvanderblah");

        assertThat(created.getSurname()).isEqualTo("Van Der Accentéd Chàractèrs");
        assertThat(created.getGivenName()).isEqualTo("François");
    }
}

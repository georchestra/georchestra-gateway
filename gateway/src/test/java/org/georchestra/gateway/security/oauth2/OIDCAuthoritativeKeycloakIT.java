package org.georchestra.gateway.security.oauth2;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OIDCAuthoritativeKeycloakIT extends AbstractOIDCKeycloakSupport {

    @Autowired
    private RoleDao roleDao;

    @DynamicPropertySource
    static void isAuthoritativeProperties(DynamicPropertyRegistry registry) {
        registry.add("georchestra.gateway.security.oauth2.authorities", () -> List.of("keycloak"));
    }

    @Test
    public void keycloakLoginCreateUserInLdapWhenUserUnknown() throws DataServiceException {
        String userId = "testoidcuser1";
        createTestUser(userId, THREE_ROLES);
        logAndFollowRedirect(userId);

        assertNotNull(accountDao.findByUID("keycloak_" + userId),
                "Account should have been created in LDAP by CreateAccountUserCustomizer");
        assertNotNull(roleDao.findByCommonName("TEST_ROLE"));
    }

    @Test
    public void keycloakLoginRewriteUserInLdapWhenUserExists() throws DataServiceException, DuplicatedEmailException {
        String userId = "testoidcuser2";
        createTestUser(userId, THREE_ROLES);
        logAndFollowRedirect(userId);
        Account account = accountDao.findByUID("keycloak_" + userId);
        Account updatedAccount = accountDao.findByUID("keycloak_" + userId);
        updatedAccount.setGivenName("mo");
        accountDao.update(account, updatedAccount);

        logAndFollowRedirect(userId);

        assertThat(accountDao.findByUID("keycloak_" + userId).getGivenName().equals("test")).isTrue();
    }

}

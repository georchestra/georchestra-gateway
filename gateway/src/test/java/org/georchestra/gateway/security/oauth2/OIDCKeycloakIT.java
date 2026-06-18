package org.georchestra.gateway.security.oauth2;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OIDCKeycloakIT extends  AbstractOIDCKeycloakSupport {

    @Test
    public void keycloakLoginCreateUserInLdapWhenUserUnknown() throws DataServiceException {
        String userId = "testoidcuser1";
        createTestUser(userId, List.of("ROLE_USER", "GRP_AWESOME_ORG"));
        logAndFollowRedirect(userId);

        assertNotNull(accountDao.findByUID("keycloak_" + userId),
                "Account should have been created in LDAP by CreateAccountUserCustomizer");
    }

    @Test
    public void keycloakLoginLetUserUnmodifiedInLdapWhenUserExists() throws DataServiceException, DuplicatedEmailException {
        String userId = "testoidcuser2";
        createTestUser(userId, List.of("ROLE_USER", "GRP_AWESOME_ORG"));
        logAndFollowRedirect(userId);
        Account account = accountDao.findByUID("keycloak_" + userId);
        Account updatedAccount = accountDao.findByUID("keycloak_" + userId);
        updatedAccount.setGivenName("mo");
        accountDao.update(account, updatedAccount);

        logAndFollowRedirect(userId);

        assertThat(accountDao.findByUID("keycloak_" + userId).getGivenName().equals("mo")).isTrue();
    }
}

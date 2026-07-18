package org.georchestra.gateway.security.oauth2;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.DuplicatedCommonNameException;
import org.georchestra.ds.roles.Role;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OIDCKeycloakIT extends AbstractOIDCKeycloakSupport {

    @Test
    public void keycloakLoginCreateUserInLdapWhenUserUnknown() throws DataServiceException {
        String userId = "testoidcuser1";
        createTestUser(userId, THREE_ROLES);

        logAndFollowRedirect(userId);

        Account account = accountDao.findByUID("keycloak_" + userId);
        assertNotNull(account, "Account should have been created in LDAP by CreateAccountUserCustomizer");
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);
    }

    @Test
    public void keycloakLoginLetUserUnmodifiedInLdapWhenUserExists()
            throws DataServiceException, DuplicatedEmailException {
        String userId = "testoidcuser2";
        createTestUser(userId, THREE_ROLES);
        logAndFollowRedirect(userId);
        Account account = accountDao.findByUID("keycloak_" + userId);
        Account updatedAccount = accountDao.findByUID("keycloak_" + userId);
        updatedAccount.setGivenName("mo");
        accountDao.update(account, updatedAccount);

        logAndFollowRedirect(userId);

        assertThat(accountDao.findByUID("keycloak_" + userId).getGivenName().equals("mo")).isTrue();
    }

    @Test
    public void keycloakLoginLetUserUnmodifiedWithROLEPrefixedRole()
            throws DataServiceException, DuplicatedEmailException, DuplicatedCommonNameException {
        String userId = "testoidcuser3";
        createTestUser(userId, FOUR_ROLES);
        logAndFollowRedirect(userId);

        createTestUser(userId, THREE_ROLES);
        logAndFollowRedirect(userId);

        Account account = accountDao.findByUID("keycloak_" + userId);
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "PREFIX", "USER", "OIDC_USER"), roles);
    }

}

package org.georchestra.gateway.security.oauth2;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.DuplicatedCommonNameException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.roles.Role;
import org.georchestra.ds.roles.RoleFactory;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OIDCAuthoritativeKeycloakIT extends AbstractOIDCKeycloakSupport {

    @DynamicPropertySource
    static void isAuthoritativeProperties(DynamicPropertyRegistry registry) {
        registry.add("georchestra.gateway.security.oauth2.authorities", () -> List.of("keycloak"));
    }

    @Test
    public void keycloakLoginCreateUserInLdapWhenUserUnknown() throws DataServiceException {
        String userId = "testoidcuser1";
        UserRepresentation keycloakUser = createTestUser(userId, random(), THREE_ROLES);
        String expectedOrg = keycloakUser.getFirstName();

        logAndFollowRedirect(userId);

        Account account = accountDao.findByUID("keycloak_" + userId);
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);
        Org orgInLdap = orgsDao.findByCommonName(expectedOrg);
        assertThat(orgInLdap.getMembers().contains(account.getUid())).isTrue();
    }

    @Test
    public void keycloakLoginRewriteUserInLdapWhenUserExists()
            throws DataServiceException, DuplicatedEmailException, DuplicatedCommonNameException {
        String userId = "testoidcuser2";
        UserRepresentation keycloakUser = createTestUser(userId, random(), THREE_ROLES);
        logAndFollowRedirect(userId);
        Account account = accountDao.findByUID("keycloak_" + userId);
        Account updatedAccount = accountDao.findByUID("keycloak_" + userId);
        updatedAccount.setGivenName("mo");
        accountDao.update(account, updatedAccount);
        String roleName = "role_" + random();
        roleDao.insert(RoleFactory.create(roleName, "", false));
        roleDao.addUser(roleName, updatedAccount);
        roleDao.deleteUser("TEST_ROLE", updatedAccount);

        logAndFollowRedirect(userId);

        account = accountDao.findByUID("keycloak_" + userId);
        assertThat(account.getGivenName().equals(keycloakUser.getFirstName())).isTrue();
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);
    }

    @Test
    public void keycloakLoginRewriteUserWithROLEPrefixedRoleAndUpdatedOrg()
            throws DataServiceException, DuplicatedEmailException, DuplicatedCommonNameException {
        String userId = "testoidcuser3";
        UserRepresentation keycloakUser = createTestUser(userId, random(), THREE_ROLES);
        String initialOrg = keycloakUser.getFirstName();
        logAndFollowRedirect(userId);

        keycloakUser = updateTestUser(userId, random(), FOUR_ROLES);
        String updatedOrg = keycloakUser.getFirstName();
        logAndFollowRedirect(userId);

        Account account = accountDao.findByUID("keycloak_" + userId);
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "PREFIX", "USER", "OIDC_USER"), roles);
        Org initialOrgInLdap = orgsDao.findByCommonName(initialOrg);
        assertThat(initialOrgInLdap.getMembers().contains(account.getUid())).isFalse();
        Org updatedOrgInLdap = orgsDao.findByCommonName(updatedOrg);
        assertThat(updatedOrgInLdap.getMembers().contains(account.getUid())).isTrue();
    }

}

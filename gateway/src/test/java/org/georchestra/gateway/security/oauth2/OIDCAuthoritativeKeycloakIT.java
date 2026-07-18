package org.georchestra.gateway.security.oauth2;

import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.DuplicatedCommonNameException;
import org.georchestra.ds.roles.Role;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.roles.RoleFactory;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.DuplicatedEmailException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

        Account account = accountDao.findByUID("keycloak_" + userId);
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);
    }

    @Test
    public void keycloakLoginRewriteUserInLdapWhenUserExists() throws DataServiceException, DuplicatedEmailException, DuplicatedCommonNameException {
        String userId = "testoidcuser2";
        createTestUser(userId, THREE_ROLES);
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
        assertThat(account.getGivenName().equals("test")).isTrue();
        Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);
    }

    private static String random() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}

package org.georchestra.gateway.accounts.admin.ldap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.ldap.extended.DemultiplexingUsersApi;
import org.georchestra.gateway.security.ldap.extended.ExtendedGeorchestraUser;
import org.georchestra.gateway.security.oauth2.OpenIdConnectCustomConfig;
import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.NameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LdapAccountsManagerTest {

    public @Test void testEnsureRoleExist() throws DataServiceException {
        RoleDao roleDao = mock(RoleDao.class);
        when(roleDao.findByCommonName(anyString())).thenThrow(new NameNotFoundException("FAKE_ROLE"));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), null, roleDao, null,
                null, null, null);

        toTest.ensureRoleExists("FAKE_ROLE");
        // No exception thrown
    }

    @Test
    void verifySingleOrgMembership_acceptsNoMembershipAfterUnlink() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        when(orgsDao.findAll()).thenReturn(List.of());

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class),
                mock(AccountDao.class), mock(RoleDao.class), orgsDao, null, null, null);

        Account account = mock(Account.class);
        when(account.getUid()).thenReturn("uid-1");

        toTest.verifySingleOrgMembership(account, null);
    }

    @Test
    void verifySingleOrgMembership_acceptsExactlyOneExpectedMembership() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        Org org = new Org();
        org.setId("ORG_A");
        org.setMembers(List.of("uid-1"));
        when(orgsDao.findAll()).thenReturn(List.of(org));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(org);

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class),
                mock(AccountDao.class), mock(RoleDao.class), orgsDao, null, null, null);

        Account account = mock(Account.class);
        when(account.getUid()).thenReturn("uid-1");

        toTest.verifySingleOrgMembership(account, "ORG_A");
    }

    @Test
    void verifySingleOrgMembership_failsWhenUserInMultipleOrgs() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        Org org1 = new Org();
        org1.setId("ORG_A");
        org1.setMembers(List.of("uid-1"));
        Org org2 = new Org();
        org2.setId("ORG_B");
        org2.setMembers(List.of("uid-1"));
        when(orgsDao.findAll()).thenReturn(List.of(org1, org2));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class),
                mock(AccountDao.class), mock(RoleDao.class), orgsDao, null, null, null);

        Account account = mock(Account.class);
        when(account.getUid()).thenReturn("uid-1");

        assertThrows(IllegalStateException.class, () -> toTest.verifySingleOrgMembership(account, "ORG_A"));
    }

    @Test
    void ensureOrgExists_usesExistingLdapUidWhenOAuthUidDiffers() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        AccountDao accountDao = mock(AccountDao.class);
        RoleDao roleDao = mock(RoleDao.class);
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();
        securityConfig.setModeratedSignup(false);
        securityConfig.setDefaultOrganization("");

        OpenIdConnectCustomConfig providersConfig = new OpenIdConnectCustomConfig();
        OpenIdConnectCustomConfig proconnectConfig = new OpenIdConnectCustomConfig();
        proconnectConfig.setSearchEmail(true);
        providersConfig.getProvider().put("proconnect", proconnectConfig);

        Org existingOrg = new Org();
        existingOrg.setId("ORG_B");
        existingOrg.setMembers(new ArrayList<>());
        when(orgsDao.findByOrgUniqueId("12345678901234")).thenReturn(existingOrg);
        when(orgsDao.findByCommonName("ORG_B")).thenReturn(existingOrg);
        when(orgsDao.findAll()).thenReturn(List.of(existingOrg));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(existingOrg);

        GeorchestraUser existingUser = new GeorchestraUser();
        existingUser.setUsername("fake_uid");
        existingUser.setRoles(new ArrayList<>());
        ExtendedGeorchestraUser existingLdapUser = new ExtendedGeorchestraUser(existingUser);
        when(usersApi.findByEmail("user@example.org", false)).thenReturn(Optional.of(existingLdapUser));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, roleDao,
                orgsDao, usersApi, securityConfig, providersConfig);

        GeorchestraUser mappedUser = new GeorchestraUser();
        mappedUser.setUsername("proconnect_uid");
        mappedUser.setEmail("user@example.org");
        mappedUser.setOrganization("ORG_B");
        mappedUser.setOAuth2Provider("proconnect");
        mappedUser.setOAuth2Uid("0226c002-a462-4127-887d-ae2b03633bd9");
        mappedUser.setOAuth2OrgId("12345678901234");
        mappedUser.setRoles(new ArrayList<>());

        toTest.ensureOrgExists(mappedUser);

        assertTrue(existingOrg.getMembers().contains("fake_uid"));
        assertFalse(existingOrg.getMembers().contains("proconnect_uid"));
        verify(orgsDao).update(existingOrg);
    }

    @Test
    void ensureOrgExists_usesExistingLdapUidWhenOAuthUidDiffers_withFakeSearchByEmail() {
        OrgsDao orgsDao = mock(OrgsDao.class);
        AccountDao accountDao = mock(AccountDao.class);
        RoleDao roleDao = mock(RoleDao.class);
        DemultiplexingUsersApi usersApi = mock(DemultiplexingUsersApi.class);

        GeorchestraGatewaySecurityConfigProperties securityConfig = new GeorchestraGatewaySecurityConfigProperties();
        securityConfig.setModeratedSignup(false);
        securityConfig.setDefaultOrganization("");

        OpenIdConnectCustomConfig providersConfig = new OpenIdConnectCustomConfig();
        OpenIdConnectCustomConfig fakeConfig = new OpenIdConnectCustomConfig();
        fakeConfig.setSearchEmail(true);
        providersConfig.getProvider().put("fake", fakeConfig);

        Org existingOrg = new Org();
        existingOrg.setId("ORG_A");
        existingOrg.setMembers(new ArrayList<>());
        when(orgsDao.findByOrgUniqueId("12345678901234")).thenReturn(existingOrg);
        when(orgsDao.findByCommonName("ORG_A")).thenReturn(existingOrg);
        when(orgsDao.findAll()).thenReturn(List.of(existingOrg));
        when(orgsDao.findByUser(any(Account.class))).thenReturn(existingOrg);

        GeorchestraUser existingUser = new GeorchestraUser();
        existingUser.setUsername("proconnect_uid");
        existingUser.setRoles(new ArrayList<>());
        ExtendedGeorchestraUser existingLdapUser = new ExtendedGeorchestraUser(existingUser);
        when(usersApi.findByEmail("user@example.org", false)).thenReturn(Optional.of(existingLdapUser));

        LdapAccountsManager toTest = new LdapAccountsManager(mock(ApplicationEventPublisher.class), accountDao, roleDao,
                orgsDao, usersApi, securityConfig, providersConfig);

        GeorchestraUser mappedUser = new GeorchestraUser();
        mappedUser.setUsername("fake_uid");
        mappedUser.setEmail("user@example.org");
        mappedUser.setOrganization("ORG_A");
        mappedUser.setOAuth2Provider("fake");
        mappedUser.setOAuth2Uid("fake-external-id");
        mappedUser.setOAuth2OrgId("12345678901234");
        mappedUser.setRoles(new ArrayList<>());

        toTest.ensureOrgExists(mappedUser);

        assertTrue(existingOrg.getMembers().contains("proconnect_uid"));
        assertFalse(existingOrg.getMembers().contains("fake_uid"));
        verify(orgsDao).update(existingOrg);
    }
}

package org.georchestra.gateway.accounts.admin.ldap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.orgs.Org;
import org.georchestra.ds.orgs.OrgsDao;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.users.Account;
import org.georchestra.ds.users.AccountDao;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.NameNotFoundException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
}

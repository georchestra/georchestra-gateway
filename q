[1mdiff --git a/gateway/src/main/java/org/georchestra/gateway/accounts/admin/AbstractAccountsManager.java b/gateway/src/main/java/org/georchestra/gateway/accounts/admin/AbstractAccountsManager.java[m
[1mindex 29aad94..e3ffb00 100644[m
[1m--- a/gateway/src/main/java/org/georchestra/gateway/accounts/admin/AbstractAccountsManager.java[m
[1m+++ b/gateway/src/main/java/org/georchestra/gateway/accounts/admin/AbstractAccountsManager.java[m
[36m@@ -221,9 +221,7 @@[m [mpublic abstract class AbstractAccountsManager implements AccountManager {[m
         try {[m
             GeorchestraUser existing = findInternal(mapped).orElse(null);[m
             updateInternal(existing, mapped);[m
[31m-            // createUserOrgUniqueIdIfMissing(mapped); not tested. Cannot set[m
[31m-            // customProviderClaims with oidc,[m
[31m-            // thus cannot set setOAuth2OrgId.[m
[32m+[m[32m            createUserOrgUniqueIdIfMissing(mapped);[m
             return existing;[m
         } catch (DataServiceException | DuplicatedEmailException e) {[m
             throw new RuntimeException(e);[m
[1mdiff --git a/gateway/src/test/java/org/georchestra/gateway/security/oauth2/AbstractOIDCKeycloakSupport.java b/gateway/src/test/java/org/georchestra/gateway/security/oauth2/AbstractOIDCKeycloakSupport.java[m
[1mindex 8ca92be..1b1e626 100644[m
[1m--- a/gateway/src/test/java/org/georchestra/gateway/security/oauth2/AbstractOIDCKeycloakSupport.java[m
[1m+++ b/gateway/src/test/java/org/georchestra/gateway/security/oauth2/AbstractOIDCKeycloakSupport.java[m
[36m@@ -7,6 +7,7 @@[m [mimport org.georchestra.ds.roles.RoleDao;[m
 import org.georchestra.ds.users.AccountDao;[m
 import org.georchestra.gateway.app.GeorchestraGatewayApplication;[m
 import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;[m
[32m+[m[32mimport org.jetbrains.annotations.NotNull;[m
 import org.keycloak.admin.client.resource.RealmResource;[m
 import org.keycloak.admin.client.resource.UserResource;[m
 import org.keycloak.representations.idm.CredentialRepresentation;[m
[36m@@ -131,18 +132,8 @@[m [mpublic abstract class AbstractOIDCKeycloakSupport {[m
 [m
     protected static UserRepresentation createTestUser(String userId, String orgSuffix, String roles) {[m
         RealmResource realm = keycloak.getKeycloakAdminClient().realm("georchestra-oidc");[m
[31m-        UserRepresentation testuser = new UserRepresentation();[m
[31m-        testuser.setUsername(userId);[m
[31m-        testuser.setEmail(String.format("psc+%s@georchestra.org", userId));[m
[31m-        testuser.setFirstName("when_test_use_given_name_as_org_" + orgSuffix);[m
[31m-        testuser.setLastName("user");[m
[31m-        testuser.setEnabled(true);[m
[31m-        CredentialRepresentation pwd = new CredentialRepresentation();[m
[31m-        pwd.setTemporary(false);[m
[31m-        pwd.setType(CredentialRepresentation.PASSWORD);[m
[31m-        pwd.setValue(userId);[m
[31m-        testuser.setCredentials(List.of(pwd));[m
[31m-        Response response = realm.users().create(testuser);[m
[32m+[m[32m        UserRepresentation testUser = createUserRepresentation(userId, orgSuffix);[m
[32m+[m[32m        Response response = realm.users().create(testUser);[m
         response.close();[m
 [m
         RoleRepresentation rolesToAdd = realm.clients().get(GEOR_CLIENT_ID).roles().list().stream()[m
[36m@@ -151,7 +142,22 @@[m [mpublic abstract class AbstractOIDCKeycloakSupport {[m
         List<RoleRepresentation> toClear = userToComplete.roles().clientLevel(GEOR_CLIENT_ID).listEffective();[m
         userToComplete.roles().clientLevel(GEOR_CLIENT_ID).remove(toClear);[m
         userToComplete.roles().clientLevel(GEOR_CLIENT_ID).add(List.of(rolesToAdd));[m
[31m-        return testuser;[m
[32m+[m[32m        return testUser;[m
[32m+[m[32m    }[m
[32m+[m
[32m+[m[32m    protected static UserRepresentation updateTestUser(String userId, String orgSuffix, String roles) {[m
[32m+[m[32m        RealmResource realm = keycloak.getKeycloakAdminClient().realm("georchestra-oidc");[m
[32m+[m[32m        UserRepresentation testUser = createUserRepresentation(userId, orgSuffix);[m
[32m+[m[32m        UserResource userToUpdate = realm.users().get(realm.users().searchByUsername(userId, true).get(0).getId());[m
[32m+[m[32m        userToUpdate.update(testUser);[m
[32m+[m
[32m+[m[32m        RoleRepresentation rolesToAdd = realm.clients().get(GEOR_CLIENT_ID).roles().list().stream()[m
[32m+[m[32m                .filter(x -> roles.equals(x.getName())).findFirst().get();[m
[32m+[m[32m        UserResource userToComplete = realm.users().get(realm.users().searchByUsername(userId, true).get(0).getId());[m
[32m+[m[32m        List<RoleRepresentation> toClear = userToComplete.roles().clientLevel(GEOR_CLIENT_ID).listEffective();[m
[32m+[m[32m        userToComplete.roles().clientLevel(GEOR_CLIENT_ID).remove(toClear);[m
[32m+[m[32m        userToComplete.roles().clientLevel(GEOR_CLIENT_ID).add(List.of(rolesToAdd));[m
[32m+[m[32m        return testUser;[m
     }[m
 [m
     protected void logAndFollowRedirect(String userId) {[m
[36m@@ -188,4 +194,19 @@[m [mpublic abstract class AbstractOIDCKeycloakSupport {[m
     protected String random() {[m
         return UUID.randomUUID().toString().substring(0, 6);[m
     }[m
[32m+[m
[32m+[m[32m    private static @NotNull UserRepresentation createUserRepresentation(String userId, String orgSuffix) {[m
[32m+[m[32m        UserRepresentation testUser = new UserRepresentation();[m
[32m+[m[32m        testUser.setUsername(userId);[m
[32m+[m[32m        testUser.setEmail(String.format("psc+%s@georchestra.org", userId));[m
[32m+[m[32m        testUser.setFirstName("when_test_use_given_name_as_org_" + orgSuffix);[m
[32m+[m[32m        testUser.setLastName("user");[m
[32m+[m[32m        testUser.setEnabled(true);[m
[32m+[m[32m        CredentialRepresentation pwd = new CredentialRepresentation();[m
[32m+[m[32m        pwd.setTemporary(false);[m
[32m+[m[32m        pwd.setType(CredentialRepresentation.PASSWORD);[m
[32m+[m[32m        pwd.setValue(userId);[m
[32m+[m[32m        testUser.setCredentials(List.of(pwd));[m
[32m+[m[32m        return testUser;[m
[32m+[m[32m    }[m
 }[m
[1mdiff --git a/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCAuthoritativeKeycloakIT.java b/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCAuthoritativeKeycloakIT.java[m
[1mindex cc2a3d5..363d16e 100644[m
[1m--- a/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCAuthoritativeKeycloakIT.java[m
[1m+++ b/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCAuthoritativeKeycloakIT.java[m
[36m@@ -2,12 +2,14 @@[m [mpackage org.georchestra.gateway.security.oauth2;[m
 [m
 import org.georchestra.ds.DataServiceException;[m
 import org.georchestra.ds.DuplicatedCommonNameException;[m
[32m+[m[32mimport org.georchestra.ds.orgs.Org;[m
 import org.georchestra.ds.roles.Role;[m
 import org.georchestra.ds.roles.RoleFactory;[m
 import org.georchestra.ds.users.Account;[m
 import org.georchestra.ds.users.DuplicatedEmailException;[m
 import org.junit.jupiter.api.Test;[m
 import org.keycloak.representations.idm.UserRepresentation;[m
[32m+[m[32mimport org.springframework.ldap.NameNotFoundException;[m
 import org.springframework.test.context.DynamicPropertyRegistry;[m
 import org.springframework.test.context.DynamicPropertySource;[m
 [m
[36m@@ -17,6 +19,8 @@[m [mimport java.util.stream.Collectors;[m
 [m
 import static org.assertj.core.api.Assertions.assertThat;[m
 import static org.junit.jupiter.api.Assertions.assertEquals;[m
[32m+[m[32mimport static org.junit.jupiter.api.Assertions.assertNotNull;[m
[32m+[m[32mimport static org.junit.jupiter.api.Assertions.assertThrows;[m
 [m
 public class OIDCAuthoritativeKeycloakIT extends AbstractOIDCKeycloakSupport {[m
 [m
[36m@@ -28,13 +32,16 @@[m [mpublic class OIDCAuthoritativeKeycloakIT extends AbstractOIDCKeycloakSupport {[m
     @Test[m
     public void keycloakLoginCreateUserInLdapWhenUserUnknown() throws DataServiceException {[m
         String userId = "testoidcuser1";[m
[31m-        createTestUser(userId, random(), THREE_ROLES);[m
[32m+[m[32m        UserRepresentation keycloakUser = createTestUser(userId, random(), THREE_ROLES);[m
[32m+[m[32m        String expectedOrg = keycloakUser.getFirstName();[m
 [m
         logAndFollowRedirect(userId);[m
 [m
         Account account = accountDao.findByUID("keycloak_" + userId);[m
         Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());[m
         assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "OTHER_ROLE", "USER", "OIDC_USER"), roles);[m
[32m+[m[32m        Org orgInLdap = orgsDao.findByCommonName(expectedOrg);[m
[32m+[m[32m        assertThat(orgInLdap.getMembers().contains(account.getUid())).isTrue();[m
     }[m
 [m
     @Test[m
[36m@@ -61,18 +68,24 @@[m [mpublic class OIDCAuthoritativeKeycloakIT extends AbstractOIDCKeycloakSupport {[m
     }[m
 [m
     @Test[m
[31m-    public void keycloakLoginRewriteUserWithROLEPrefixedRole()[m
[32m+[m[32m    public void keycloakLoginRewriteUserWithROLEPrefixedRoleAndUpdatedOrg()[m
             throws DataServiceException, DuplicatedEmailException, DuplicatedCommonNameException {[m
         String userId = "testoidcuser3";[m
[31m-        createTestUser(userId, random(), THREE_ROLES);[m
[32m+[m[32m        UserRepresentation keycloakUser = createTestUser(userId, random(), THREE_ROLES);[m
[32m+[m[32m        String initialOrg = keycloakUser.getFirstName();[m
         logAndFollowRedirect(userId);[m
 [m
[31m-        createTestUser(userId, random(), FOUR_ROLES);[m
[32m+[m[32m        keycloakUser = updateTestUser(userId, random(), FOUR_ROLES);[m
[32m+[m[32m        String updatedOrg = keycloakUser.getFirstName();[m
         logAndFollowRedirect(userId);[m
 [m
         Account account = accountDao.findByUID("keycloak_" + userId);[m
         Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());[m
         assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "PREFIX", "USER", "OIDC_USER"), roles);[m
[32m+[m[32m        Org initialOrgInLdap = orgsDao.findByCommonName(initialOrg);[m
[32m+[m[32m        assertThat(initialOrgInLdap.getMembers().contains(account.getUid())).isFalse();[m
[32m+[m[32m        Org updatedOrgInLdap = orgsDao.findByCommonName(updatedOrg);[m
[32m+[m[32m        assertThat(updatedOrgInLdap.getMembers().contains(account.getUid())).isTrue();[m
     }[m
 [m
 }[m
[1mdiff --git a/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCKeycloakIT.java b/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCKeycloakIT.java[m
[1mindex 246d3af..a3cecf5 100644[m
[1m--- a/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCKeycloakIT.java[m
[1m+++ b/gateway/src/test/java/org/georchestra/gateway/security/oauth2/OIDCKeycloakIT.java[m
[36m@@ -7,6 +7,8 @@[m [mimport org.georchestra.ds.roles.Role;[m
 import org.georchestra.ds.users.Account;[m
 import org.georchestra.ds.users.DuplicatedEmailException;[m
 import org.junit.jupiter.api.Test;[m
[32m+[m[32mimport org.keycloak.representations.idm.UserRepresentation;[m
[32m+[m[32mimport org.springframework.ldap.NameNotFoundException;[m
 [m
 import java.util.Set;[m
 import java.util.stream.Collectors;[m
[36m@@ -14,6 +16,7 @@[m [mimport java.util.stream.Collectors;[m
 import static org.assertj.core.api.Assertions.assertThat;[m
 import static org.junit.jupiter.api.Assertions.assertEquals;[m
 import static org.junit.jupiter.api.Assertions.assertNotNull;[m
[32m+[m[32mimport static org.junit.jupiter.api.Assertions.assertThrows;[m
 [m
 public class OIDCKeycloakIT extends AbstractOIDCKeycloakSupport {[m
 [m
[36m@@ -53,15 +56,21 @@[m [mpublic class OIDCKeycloakIT extends AbstractOIDCKeycloakSupport {[m
     public void keycloakLoginLetUserUnmodifiedWithROLEPrefixedRole()[m
             throws DataServiceException, DuplicatedEmailException, DuplicatedCommonNameException {[m
         String userId = "testoidcuser3";[m
[31m-        createTestUser(userId, random(), FOUR_ROLES);[m
[32m+[m[32m        UserRepresentation keycloakUser = createTestUser(userId, random(), FOUR_ROLES);[m
[32m+[m[32m        String initialOrg = keycloakUser.getFirstName();[m
         logAndFollowRedirect(userId);[m
 [m
[31m-        createTestUser(userId, random(), THREE_ROLES);[m
[32m+[m[32m        keycloakUser = createTestUser(userId, random(), THREE_ROLES);[m
[32m+[m[32m        String updatedOrg = keycloakUser.getFirstName();[m
         logAndFollowRedirect(userId);[m
 [m
         Account account = accountDao.findByUID("keycloak_" + userId);[m
         Set<String> roles = roleDao.findAllForUser(account).stream().map(Role::getName).collect(Collectors.toSet());[m
         assertEquals(Set.of("TEST_ROLE", "APPS_GEORCHESTRA", "PREFIX", "USER", "OIDC_USER"), roles);[m
[32m+[m[32m        Org org = orgsDao.findByCommonName(initialOrg);[m
[32m+[m[32m        assertNotNull(org, "Org should have been created in LDAP");[m
[32m+[m[32m        assertThat(org.getMembers().contains(account.getUid())).isTrue();[m
[32m+[m[32m        assertThrows(NameNotFoundException.class, () -> orgsDao.findByCommonName(updatedOrg));[m
     }[m
 [m
 }[m

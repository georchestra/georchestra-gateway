/*
 * Copyright (C) 2023 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.gateway.security;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.security.oauth2.OAuth2Configuration;
import org.georchestra.gateway.security.oauth2.OAuth2ConfigurationProperties;
import org.georchestra.gateway.security.oauth2.OAuth2ConfigurationProperties.OpenIdConnectCustomClaimsConfigProperties.OidcRolesMappingConfig;
import org.georchestra.gateway.security.oauth2.OAuth2UserMapper;
import org.georchestra.gateway.security.oauth2.OpenIdConnectUserMapper;
import org.georchestra.gateway.test.context.support.WithMockOAuth2User;
import org.georchestra.gateway.test.context.support.WithMockOidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verify that {@link GrantedAuthority} names get first translated to role names
 * (e.g. {@literal GP.TEST.SAMPLE} -> {@literal GP_TEST_SAMPLE}) and then the
 * translated role names are used to add extra roles as defined by
 * {@code georchestra.gateway.roles-mappings.*} properties
 */
@SpringBootTest(classes = { //
        GeorchestraGatewayApplication.class, //
        MockUserGeorchestraUserMapperConfiguration.class, //
        RolesMappingsUserCustomizerIT.ForceOidcUserMapperBean.class//
}, webEnvironment = WebEnvironment.MOCK, properties = { "georchestra.datadir=../datadir", //
        "georchestra.gateway.security.ldap.default.enabled=false", //
        "georchestra.gateway.security.oauth2.enabled=false",//
})
@AutoConfigureWebTestClient(timeout = "PT120S")
class RolesMappingsUserCustomizerIT {

    private @Autowired WebTestClient testClient;

    private @Autowired OAuth2ConfigurationProperties config;

    /**
     * Bypass {@link OAuth2Configuration} since
     * {@code georchestra.gateway.security.oauth2.enabled=false} but contribute
     * {@link OAuth2ConfigurationProperties} and {@link OpenIdConnectUserMapper} to
     * resolve tokens from {@link WithMockOidcUser @WithMockOidcUser}.
     */
    @Configuration
    @EnableConfigurationProperties({ OAuth2ConfigurationProperties.class })
    static class ForceOidcUserMapperBean {
        @Bean
        OAuth2UserMapper oAuth2GeorchestraUserUserMapper(OAuth2ConfigurationProperties config) {
            return new OAuth2UserMapper(config);
        }

        @Bean
        OpenIdConnectUserMapper openIdConnectGeorchestraUserUserMapper(OAuth2ConfigurationProperties config) {
            return new OpenIdConnectUserMapper(config);
        }
    }

    private void verifyMappedUser(String expected) {
        testClient.get().uri("/whoami").exchange()//
                .expectStatus().isOk()//
                .expectBody()//
//		.consumeWith(e -> System.err.println(new String(e.getResponseBodyContent())))
                .json(expected);

    }

    @WithMockUser(authorities = { "GP.TEST.SAMPLE" })
    public @Test void role_prefix_added_but_role_name_not_changed_if_token_is_not_oauth() {

        verifyMappedUser("{\"GeorchestraUser\":{\"roles\":[\"ROLE_GP.TEST.SAMPLE\"]}}");
    }

    @WithMockOAuth2User(authorities = { "GP.TEST.SAMPLE", "OAuth2 Sample Authority" })
    public @Test void oauth2_provided_authorities_are_normalized_and_prefixed() {

        verifyMappedUser("{\"GeorchestraUser\":{\"username\":\"test-user\","
                + "\"roles\":[\"ROLE_GP_TEST_SAMPLE\",\"ROLE_OAUTH2_SAMPLE_AUTHORITY\"]}}");
    }

    @WithMockOAuth2User(authorities = { "GP.TEST.SAMPLE", "OAuth2 Sample Authority" })
    public @Test void oauth2_provided_authorities_are_prefixed_but_not_normalized() {
        config.getRoles().setNormalize(false);
        config.getRoles().setUppercase(false);

        verifyMappedUser("{\"GeorchestraUser\":{\"username\":\"test-user\","
                + "\"roles\":[\"ROLE_GP.TEST.SAMPLE\",\"ROLE_OAuth2 Sample Authority\"]}}");
    }

    /**
     * The provided {@link OAuth2AuthenticationToken#getAuthorities() authority
     * names} are normalized,
     * {@code georchestra.gateway.security.oidc.roles.normalize|uppercase} only
     * apply to roles extracted from non standard claims (
     * {@code georchestra.gateway.security.oidc.roles.json.path.*})
     * 
     */
    @WithMockOidcUser(authorities = { "GP.OIDC.ROLE1" })
    public @Test void oidc_provided_authorities_are_normalized_and_prefixed() {

        config.getOidc().getClaims().getRoles().setNormalize(true);
        config.getOidc().getClaims().getRoles().setUppercase(true);

        verifyMappedUser("{\"GeorchestraUser\":{\"roles\":[\"ROLE_GP_OIDC_ROLE1\"]}}");
    }

    @WithMockOidcUser(authorities = { "GP.OIDC.ROLE1" })
    public @Test void oidc_provided_authorities_are_prefixed_but_not_normalized() {

        config.getOidc().getClaims().getRoles().setNormalize(false);
        config.getOidc().getClaims().getRoles().setUppercase(false);

        verifyMappedUser("{\"GeorchestraUser\":{\"roles\":[\"ROLE_GP.OIDC.ROLE1\"]}}");
    }

    @WithMockOidcUser(//
            authorities = { "AUTHORITY_1" }, //
            nonStandardClaims = { //
                    "permission=GP.OIDC.ROLE 1, GP.OIDC.ROLE 2"//
            })
    public @Test void oidc_roles_from_non_standard_claims_normalized_and_role_prefix_added() {

        OidcRolesMappingConfig oidcRolesMappingConfig = config.getOidc().getClaims().getRoles();
        oidcRolesMappingConfig.getJson().setSplit(true);
        oidcRolesMappingConfig.getJson().getPath().add("$.permission");
        oidcRolesMappingConfig.setNormalize(true);
        oidcRolesMappingConfig.setUppercase(true);

        verifyMappedUser("{\"GeorchestraUser\":{\"username\":\"user\","
                + "\"roles\":[\"ROLE_AUTHORITY_1\",\"ROLE_GP_OIDC_ROLE_1\",\"ROLE_GP_OIDC_ROLE_2\"]}}");
    }

    @WithMockOidcUser(//
            authorities = { "AUTHORITY_1" }, //
            nonStandardClaims = { //
                    "permission=GP.OIDC.ROLE 1, GP.OIDC.ROLE 2"//
            })
    public @Test void oidc_roles_from_non_standard_claims_prefixed_but_not_normalized() {

        OidcRolesMappingConfig oidcRolesMappingConfig = config.getOidc().getClaims().getRoles();
        oidcRolesMappingConfig.setNormalize(false);
        oidcRolesMappingConfig.setUppercase(false);
        oidcRolesMappingConfig.getJson().setSplit(true);
        oidcRolesMappingConfig.getJson().getPath().add("$.permission");

        verifyMappedUser("{\"GeorchestraUser\":{\"username\":\"user\","
                + "\"roles\":[\"ROLE_AUTHORITY_1\",\"ROLE_GP.OIDC.ROLE 1\",\"ROLE_GP.OIDC.ROLE 2\"]}}");
    }
}

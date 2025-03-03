/*
 * Copyright (C) 2022 by the geOrchestra PSC
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.gateway.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.georchestra.security.model.GeorchestraUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.AddressStandardClaim;
import org.springframework.security.oauth2.core.oidc.StandardClaimAccessor;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.util.JSONUtils;

/**
 * 
 */
class OpenIdConnectUserMapperTest {

    OpenIdConnectUserMapper mapper;
    OpenIdConnectCustomClaimsConfigProperties nonStandardClaimsConfig;
    ExtendedOAuth2ClientProperties properties;

    /**
     * @throws java.lang.Exception
     */
    @BeforeEach
    void setUp() throws Exception {
        nonStandardClaimsConfig = new OpenIdConnectCustomClaimsConfigProperties();
        mapper = new OpenIdConnectUserMapper(nonStandardClaimsConfig);
    }

    @Test
    void applyStandardClaims() {
        StandardClaimAccessor standardClaims = mock(StandardClaimAccessor.class);
        when(standardClaims.getSubject()).thenReturn("b7f3dd13-f9cc-4573-8482-b4fccf8e1977");
        when(standardClaims.getPreferredUsername()).thenReturn("tesuser");
        when(standardClaims.getGivenName()).thenReturn("John");
        when(standardClaims.getFamilyName()).thenReturn("Doe");
        when(standardClaims.getEmail()).thenReturn("jdoe@test.com");
        when(standardClaims.getPhoneNumber()).thenReturn("+123");

        AddressStandardClaim address = mock(AddressStandardClaim.class);
        when(address.getFormatted()).thenReturn("123 test avenue");
        when(standardClaims.getAddress()).thenReturn(address);

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyStandardClaims(standardClaims, target);

        assertEquals(standardClaims.getSubject(), target.getId());
        assertEquals(standardClaims.getPreferredUsername(), target.getUsername());
        assertEquals(standardClaims.getGivenName(), target.getFirstName());
        assertEquals(standardClaims.getFamilyName(), target.getLastName());
        assertEquals(standardClaims.getEmail(), target.getEmail());
        assertEquals(standardClaims.getPhoneNumber(), target.getTelephoneNumber());
        assertEquals(address.getFormatted(), target.getPostalAddress());
    }

    @Test
    void applyNonStandardClaims_jsonPath_nested_array_single_value_to_roles() throws ParseException {
        final String jsonPath = "$.groups_json..['name']";
        nonStandardClaimsConfig.getRoles().getJson().getPath().add(jsonPath);

        final String json = //
                "{" //
                        + "'groups_json': [ [ " //
                        + "  { " //
                        + "    'name': 'GDI Planer (extern)', "//
                        + "    'targetSystem': 'gdi', "//
                        + "    'parameter': [] "//
                        + "  } " //
                        + "] ] " //
                        + "}";

        Map<String, Object> claims = sampleClaims(json);

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyGeorchestraNonStandardClaims(claims, target);

        assertEquals(List.of("GDI_PLANER_EXTERN"), target.getRoles());
    }

    @Test
    void applyNonStandardClaims_jsonPath_nested_array_multiple_values_to_roles() throws ParseException {

        final String jsonPath = "$.groups_json..['name']";
        nonStandardClaimsConfig.getRoles().getJson().getPath().add(jsonPath);

        final String json = //
                "{" //
                        + "'groups_json': [ [ " //
                        + "  { " //
                        + "    'name': 'GDI Planer (extern)', "//
                        + "    'targetSystem': 'gdi' "//
                        + "  }, " //
                        + "  { " //
                        + "    'name': 'GDI Editor (extern)', "//
                        + "    'targetSystem': 'gdi' "//
                        + "  } " //
                        + "] ] " //
                        + "}";

        Map<String, Object> claims = sampleClaims(json);

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyGeorchestraNonStandardClaims(claims, target);

        List<String> expected = List.of("GDI_PLANER_EXTERN", "GDI_EDITOR_EXTERN");
        List<String> actual = target.getRoles();
        assertEquals(expected, actual);
    }

    @Test
    void applyNonStandardClaims_jsonPath_multiple_json_paths() throws ParseException {
        final String orgJsonPath = "$.concat(\"ORG_\", $.PartyOrganisationID)";
        final String groupsJsonPath = "$.groups_json..['name']";

        nonStandardClaimsConfig.getRoles().getJson().getPath().add(orgJsonPath);
        nonStandardClaimsConfig.getRoles().getJson().getPath().add(groupsJsonPath);

        final String json = //
                "{" //
                        + "'groups_json': [ [ " //
                        + "  { " //
                        + "    'name': 'GDI Planer (extern)', "//
                        + "    'targetSystem': 'gdi' "//
                        + "  }, " //
                        + "  { " //
                        + "    'name': 'GDI Editor (extern)', "//
                        + "    'targetSystem': 'gdi' "//
                        + "  } " //
                        + "] ], " //
                        + "'PartyOrganisationID': '6007280321'" + "}";

        Map<String, Object> claims = sampleClaims(json);

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyGeorchestraNonStandardClaims(claims, target);

        List<String> expected = List.of("ORG_6007280321", "GDI_PLANER_EXTERN", "GDI_EDITOR_EXTERN");
        List<String> actual = target.getRoles();
        assertEquals(expected, actual);
    }

    @Test
    void applyNonStandardClaims_jsonPath_to_organization() throws ParseException {

        final String jsonPath = "$.PartyOrganisationID";
        nonStandardClaimsConfig.getOrganization().getPath().add(jsonPath);

        Map<String, Object> claims = sampleClaims();
        assertThat(claims.get("PartyOrganisationID")).isEqualTo("6007280321");

        GeorchestraUser target = new GeorchestraUser();
        target.setOrganization("unexpected");
        mapper.applyGeorchestraNonStandardClaims(claims, target);

        String expected = "6007280321";
        String actual = target.getOrganization();
        assertEquals(expected, actual);
    }

    @Test
    void applyNonStandardClaim_jsonPath_to_userId() throws Exception {
        final String icuid = "50334123";
        Map<String, Object> claims = sampleClaims();
        assertThat(claims.get("icuid")).isEqualTo(icuid);

        final String jsonPath = "$.icuid";
        nonStandardClaimsConfig.getId().getPath().add(jsonPath);

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyGeorchestraNonStandardClaims(claims, target);
        assertEquals(icuid, target.getId());
    }

    private Map<String, Object> sampleClaims() throws ParseException {
        String json = SAMPLE_CLAIMS;
        return sampleClaims(json);
    }

    private Map<String, Object> sampleClaims(String json) throws ParseException {
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) JSONUtils.parseJSON(json.replaceAll("'", "\""));
        return claims;
    }

    /**
     * Sample value for IDToken's "claims": {...}
     */
    private static final String SAMPLE_CLAIMS = //
            "{\n" //
                    + "          'at_hash': 'YuZBluv2Ehrn_nEqNi0NzA',\n" //
                    + "          'icuid': '50334123',\n" //
                    + "          'sub': 'b7f3dd13-f9cc-4573-8482-b4fccf8e1977',\n" //
                    + "          'groups_json': [\n" //
                    + "            [\n" //
                    + "              {\n" //
                    + "                'parameter': [\n" //
                    + "                  \n" //
                    + "                ],\n" //
                    + "                'name': 'GDI Planer (extern)',\n" //
                    + "                'targetSystem': 'gdi'\n" //
                    + "              },\n" //
                    + "              {\n" //
                    + "                'parameter': [\n" //
                    + "                  \n" //
                    + "                ],\n" //
                    + "                'name': 'GDI Editor (extern)',\n" //
                    + "                'targetSystem': 'gdi'\n" //
                    + "              }\n" //
                    + "            ]\n" //
                    + "          ],\n" //
                    + "          'email_verified': false,\n" //
                    + "          'iss': 'https://test.login/auth/realms/external-customer-k2',\n" //
                    + "          'typ': 'ID',\n" //
                    + "          'preferred_username': 'gabriel.roldan@test.com',\n" //
                    + "          'given_name': 'Gabriel',\n" //
                    + "          'nonce': 'p1239kUkQjqBNA7YHBjAiiYy7ULhGq-K01NiF-fm_CEI',\n" //
                    + "          'sid': 'f123a5b6-a326-4cbe-8af0-75e6f633f0b9',\n" //
                    + "          'PartyOrganisationID': '6007280321',\n" //
                    + "          'aud': [\n" //
                    + "            'gdi'\n" //
                    + "          ],\n" //
                    + "          'azp': 'gdi',\n" //
                    + "          'auth_time': 1681387195,\n" //
                    + "          'name': 'Gabriel Roldan',\n" //
                    + "          'exp': '2023-04-13T12:04:57Z',\n" //
                    + "          'session_state': 'f123a5b6-a326-4cbe-8af0-75e6f633f0b9',\n" //
                    + "          'iat': '2023-04-13T11:59:57Z',\n" //
                    + "          'family_name': 'Roldan',\n" //
                    + "          'jti': 'dc886b41-9d9c-4652-b9c7-8f160c037ccc',\n" //
                    + "          'email': 'gabriel.roldan@test.com'\n" //
                    + "        }";
}

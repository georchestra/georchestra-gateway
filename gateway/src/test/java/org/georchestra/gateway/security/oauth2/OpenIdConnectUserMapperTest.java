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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.gateway.security.oauth2;

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

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) JSONUtils.parseJSON(json.replaceAll("'", "\""));

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyNonStandardClaims(claims, target);

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

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) JSONUtils.parseJSON(json.replaceAll("'", "\""));

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyNonStandardClaims(claims, target);

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

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) JSONUtils.parseJSON(json.replaceAll("'", "\""));

        GeorchestraUser target = new GeorchestraUser();
        mapper.applyNonStandardClaims(claims, target);

        List<String> expected = List.of("ORG_6007280321", "GDI_PLANER_EXTERN", "GDI_EDITOR_EXTERN");
        List<String> actual = target.getRoles();
        assertEquals(expected, actual);
    }

    @Test
    void applyNonStandardClaims_jsonPath_to_organization() throws ParseException {

        final String jsonPath = "$.PartyOrganisationID";
        nonStandardClaimsConfig.getOrganization().getPath().add(jsonPath);

        final String json = "{'PartyOrganisationID': '6007280321'}";

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) JSONUtils.parseJSON(json.replaceAll("'", "\""));

        GeorchestraUser target = new GeorchestraUser();
        target.setOrganization("unexpected");
        mapper.applyNonStandardClaims(claims, target);

        String expected = "6007280321";
        String actual = target.getOrganization();
        assertEquals(expected, actual);
    }

}

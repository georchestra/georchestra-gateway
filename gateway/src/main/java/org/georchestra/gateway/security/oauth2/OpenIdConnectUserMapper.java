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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.security.model.GeorchestraUser;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.AddressStandardClaim;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimAccessor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps an OpenID authenticated {@link OidcUser user} to a
 * {@link GeorchestraUser}.
 * <p>
 * {@link StandardClaimAccessor standard claims} map as follow:
 * <ul>
 * <li>{@link StandardClaimAccessor#getSubject() subject} to
 * {@link GeorchestraUser#getId() id}
 * <li>{@link StandardClaimAccessor#getPreferredUsername preferredUsername} or
 * {@link StandardClaimAccessor#getEmail email} to
 * {@link GeorchestraUser#setUsername username}, in that order of precedence.
 * <li>{@link StandardClaimAccessor#getGivenName givenName} to
 * {@link GeorchestraUser#setFirstName firstName}
 * <li>{@link StandardClaimAccessor#getEmail email} to
 * {@link GeorchestraUser#setEmail email}
 * <li>{@link StandardClaimAccessor#getPhoneNumber phoneNumber} to
 * {@link GeorchestraUser#setTelephoneNumber telephoneNumber}
 * <li>{@link AddressStandardClaim#getFormatted address.formatted} to
 * {@link GeorchestraUser#setPostalAddress postalAddress}
 * </ul>
 * <p>
 * Non-standard claims can be used to set {@link GeorchestraUser#setRoles roles}
 * and {@link GeorchestraUser#setOrganization organization} short name by
 * externalized configuration of
 * {@link OpenIdConnectCustomClaimsConfigProperties}, using a JSONPath
 * expression with the {@link OidcUser#getClaims()} {@code Map<String, Object>}
 * as root object.
 * <p>
 * For example, if the OpenID Connect token contains the following claims:
 * 
 * <pre>
 * <code>
 *  { ..., 
 *    "groups_json": [[{"name":"GDI Planer"}],[{"name":"GDI Editor"}]],
 *    "PartyOrganisationID": "6007280321",
 *     ...
 *  }
 * <code>
 * </pre>
 * 
 * the following configuration in {@literal application.yml} (or other included
 * configuration file):
 * 
 * <pre>
 * {@code
 *  georchestra:
 *    gateway:
 *      security:
 *        oidc:
 *          claims:
 *           organization.path: "$.PartyOrganisationID"
 *           roles.path: "$.groups_json..['name']"
 * }
 * </pre>
 * 
 * will assign {@literal "6007280321"} to
 * {@link GeorchestraUser#setOrganization(String)}, and <strong>append<strong>
 * {@literal ["ROLE_GDI_PLANER", "ROLE_GDI_EDITOR"]} to
 * {@link GeorchestraUser#setRoles(List)}.
 * <p>
 * Additional, some control can be applied over how to map strings resolved from
 * the roles JSONPath expression to internal role names through the following
 * config properties:
 * 
 * <pre>
 * {@code
 *  georchestra.gateway.security.oidc.claims.roles:
 *   path: "$.groups_json..['name']"
 *   uppercase: true
 *   normalize: true
 *   append: true
 * }
 * </pre>
 * 
 * With the following meanings:
 * <ul>
 * <li>{@code uppercase}: Whether to return mapped role names as upper-case.
 * Defaults to {@code true}.
 * <li>{@code normalize}: Whether to remove special characters and replace
 * spaces by underscores. Defaults to {@code true}.
 * <li>{@code append}: Whether to append the resolved role names to the roles
 * given by the OAuth2 authentication. (true), or replace them (false). Defaults
 * to {@code true}.
 * </ul>
 */
@RequiredArgsConstructor
@EnableConfigurationProperties({ GeorchestraGatewaySecurityConfigProperties.class })
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class OpenIdConnectUserMapper extends OAuth2UserMapper {

    private final @NonNull OpenIdConnectCustomClaimsConfigProperties nonStandardClaimsConfig;

    private final @NonNull ExtendedOAuth2ClientProperties extendedOAuth2ClientProperties;

    protected @Override Predicate<OAuth2AuthenticationToken> tokenFilter() {
        return token -> token.getPrincipal() instanceof OidcUser;
    }

    public @Override int getOrder() {
        // be evaluated before OAuth2AuthenticationTokenUserMapper
        return Ordered.HIGHEST_PRECEDENCE;
    }

    protected @Override Optional<GeorchestraUser> map(OAuth2AuthenticationToken token) {
        GeorchestraUser user = super.map(token).orElseGet(GeorchestraUser::new);
        OidcUser oidcUser = (OidcUser) token.getPrincipal();

        String clientId = token.getAuthorizedClientRegistrationId();

        Optional<OpenIdConnectCustomClaimsConfigProperties> customProviderClaims = nonStandardClaimsConfig
                .getProviderConfig(clientId);

        try {
            // First, apply standard claims mapping between OpenID spec fields and token's
            // claims
            applyStandardClaims(oidcUser, user);
            // Next, map general georchestra claims settings and token's claims
            applyGeorchestraNonStandardClaims(oidcUser.getClaims(), user);
            // Finally, use mapping between current provider claims settings and token's
            // claims
            if (customProviderClaims.isPresent()) {
                applyProviderNonStandardClaims(customProviderClaims.get(), oidcUser.getClaims(), user);
            }
            user.setUsername((token.getAuthorizedClientRegistrationId() + "_" + user.getUsername())
                    .replaceAll("[^a-zA-Z0-9-_]", "_").toLowerCase());
        } catch (Exception e) {
            log.error("Error mapping non-standard OIDC claims for authenticated user", e);
            throw new IllegalStateException(e);
        }
        return Optional.of(user);
    }

    /**
     * @param claims OpenId Connect merged claims from {@link OidcUserInfo} and
     *               {@link OidcIdToken}
     * @param target
     */
    @VisibleForTesting
    void applyGeorchestraNonStandardClaims(Map<String, Object> claims, GeorchestraUser target) {

        nonStandardClaimsConfig.id().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setId);

        nonStandardClaimsConfig.roles().ifPresent(rolesMapper -> rolesMapper.apply(claims, target));
        nonStandardClaimsConfig.organization().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setOrganization);
    }

    @VisibleForTesting
    void applyStandardClaims(StandardClaimAccessor standardClaims, GeorchestraUser target) {
        String subjectId = standardClaims.getSubject();
        String preferredUsername = standardClaims.getPreferredUsername();
        String givenName = standardClaims.getGivenName();
        String familyName = standardClaims.getFamilyName();

        String email = standardClaims.getEmail();
        String phoneNumber = standardClaims.getPhoneNumber();

        AddressStandardClaim address = standardClaims.getAddress();
        String formattedAddress = address == null ? null : address.getFormatted();

        apply(target::setId, subjectId);
        apply(target::setUsername, preferredUsername, subjectId);
        apply(target::setFirstName, givenName);
        apply(target::setLastName, familyName);
        apply(target::setEmail, email);
        apply(target::setTelephoneNumber, phoneNumber);
        apply(target::setPostalAddress, formattedAddress);
    }

    protected void apply(Consumer<String> setter, String... alternativesInOrderOfPreference) {
        Stream.of(alternativesInOrderOfPreference).filter(Objects::nonNull).findFirst()//
                .ifPresent(setter::accept);
    }

    protected @Override Logger logger() {
        return log;
    }

    public void applyProviderNonStandardClaims(OpenIdConnectCustomClaimsConfigProperties customProviderClaims,
            Map<String, Object> claims, GeorchestraUser target) {

        customProviderClaims.id().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setId);

        customProviderClaims.roles().ifPresent(rolesMapper -> rolesMapper.apply(claims, target));

        customProviderClaims.organization().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setOrganization);

        customProviderClaims.organizationUid().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setOAuth2OrgId);

        customProviderClaims.email().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setEmail);

        customProviderClaims.familyName().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setLastName);

        customProviderClaims.givenName().map(jsonEvaluator -> jsonEvaluator.extract(claims))//
                .map(List::stream)//
                .flatMap(Stream::findFirst)//
                .ifPresent(target::setFirstName);
    }
}

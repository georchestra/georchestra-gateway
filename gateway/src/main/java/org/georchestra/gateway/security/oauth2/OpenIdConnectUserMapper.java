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
 * Maps an OpenID Connect (OIDC) authenticated {@link OidcUser} to a
 * {@link GeorchestraUser}.
 * <p>
 * The mapping follows OpenID Connect standard claims:
 * <ul>
 * <li>{@link StandardClaimAccessor#getSubject() subject} →
 * {@link GeorchestraUser#getId() id}</li>
 * <li>{@link StandardClaimAccessor#getPreferredUsername() preferredUsername} or
 * {@link StandardClaimAccessor#getEmail() email} →
 * {@link GeorchestraUser#setUsername(String) username}</li>
 * <li>{@link StandardClaimAccessor#getGivenName() givenName} →
 * {@link GeorchestraUser#setFirstName(String) firstName}</li>
 * <li>{@link StandardClaimAccessor#getFamilyName() familyName} →
 * {@link GeorchestraUser#setLastName(String) lastName}</li>
 * <li>{@link StandardClaimAccessor#getEmail() email} →
 * {@link GeorchestraUser#setEmail(String) email}</li>
 * <li>{@link StandardClaimAccessor#getPhoneNumber() phoneNumber} →
 * {@link GeorchestraUser#setTelephoneNumber(String) telephoneNumber}</li>
 * <li>{@link AddressStandardClaim#getFormatted() address.formatted} →
 * {@link GeorchestraUser#setPostalAddress(String) postalAddress}</li>
 * </ul>
 *
 * <p>
 * Non-standard claims can be mapped to {@link GeorchestraUser#setRoles(List)
 * roles} and {@link GeorchestraUser#setOrganization(String) organization} via
 * {@link OpenIdConnectCustomClaimsConfigProperties} using JSONPath expressions.
 * </p>
 *
 * <h3>Example Configuration</h3> If the OpenID Connect token contains the
 * following claims:
 * 
 * <pre>
 * {
 *   "groups_json": [[{"name":"GDI Planer"}],[{"name":"GDI Editor"}]],
 *   "PartyOrganisationID": "6007280321"
 * }
 * </pre>
 * 
 * The following configuration in {@code application.yml}:
 * 
 * <pre>
 * georchestra:
 *   gateway:
 *     security:
 *       oidc:
 *         claims:
 *           organization.path: "$.PartyOrganisationID"
 *           roles.path: "$.groups_json..['name']"
 * </pre>
 * 
 * Will:
 * <ul>
 * <li>Assign {@code "6007280321"} to
 * {@link GeorchestraUser#setOrganization(String)}</li>
 * <li>Append {@code ["ROLE_GDI_PLANER", "ROLE_GDI_EDITOR"]} to
 * {@link GeorchestraUser#setRoles(List)}</li>
 * </ul>
 *
 * <h3>Role Mapping Customization</h3> Additional customization for role name
 * formatting:
 * 
 * <pre>
 * georchestra.gateway.security.oidc.claims.roles:
 *   path: "$.groups_json..['name']"
 *   uppercase: true
 *   normalize: true
 *   append: true
 * </pre>
 * 
 * Where:
 * <ul>
 * <li>{@code uppercase}: Convert role names to uppercase (default:
 * {@code true}).</li>
 * <li>{@code normalize}: Remove special characters and replace spaces with
 * underscores (default: {@code true}).</li>
 * <li>{@code append}: Append roles to those provided by the authentication,
 * rather than replacing them (default: {@code true}).</li>
 * </ul>
 */
@RequiredArgsConstructor
@EnableConfigurationProperties({ GeorchestraGatewaySecurityConfigProperties.class })
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class OpenIdConnectUserMapper extends OAuth2UserMapper {

    private final @NonNull OpenIdConnectCustomClaimsConfigProperties nonStandardClaimsConfig;

    /**
     * Filters authentication tokens to process only {@link OidcUser}-based
     * authentication.
     *
     * @return Predicate that checks if the principal is an instance of
     *         {@link OidcUser}.
     */
    protected @Override Predicate<OAuth2AuthenticationToken> tokenFilter() {
        return token -> token.getPrincipal() instanceof OidcUser;
    }

    /**
     * Ensures this mapper runs before the generic {@link OAuth2UserMapper}.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE} to prioritize this mapper.
     */
    public @Override int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Maps an OpenID Connect (OIDC) authenticated user to a
     * {@link GeorchestraUser}.
     *
     * @param token The {@link OAuth2AuthenticationToken} containing the
     *              authentication information.
     * @return An {@link Optional} containing the mapped {@link GeorchestraUser}, or
     *         empty if mapping fails.
     */
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
     * Applies non-standard claims to the {@link GeorchestraUser} based on
     * {@link OpenIdConnectCustomClaimsConfigProperties}.
     *
     * @param claims OpenID Connect claims extracted from {@link OidcUserInfo} and
     *               {@link OidcIdToken}.
     * @param target The {@link GeorchestraUser} to update.
     */
    @VisibleForTesting
    void applyGeorchestraNonStandardClaims(Map<String, Object> claims, GeorchestraUser target) {

        nonStandardClaimsConfig.id().map(jsonEvaluator -> jsonEvaluator.extract(claims)).map(List::stream)
                .flatMap(Stream::findFirst).ifPresent(target::setId);

        nonStandardClaimsConfig.roles().ifPresent(rolesMapper -> rolesMapper.apply(claims, target));

        nonStandardClaimsConfig.organization().map(jsonEvaluator -> jsonEvaluator.extract(claims)).map(List::stream)
                .flatMap(Stream::findFirst).ifPresent(target::setOrganization);
    }

    /**
     * Applies standard OpenID Connect claims to a {@link GeorchestraUser}.
     *
     * @param standardClaims The OIDC standard claims.
     * @param target         The user to populate with standard claim values.
     */
    @VisibleForTesting
    void applyStandardClaims(StandardClaimAccessor standardClaims, GeorchestraUser target) {
        apply(target::setId, standardClaims.getSubject());
        apply(target::setUsername, standardClaims.getPreferredUsername(), standardClaims.getSubject());
        apply(target::setFirstName, standardClaims.getGivenName());
        apply(target::setLastName, standardClaims.getFamilyName());
        apply(target::setEmail, standardClaims.getEmail());
        apply(target::setTelephoneNumber, standardClaims.getPhoneNumber());

        AddressStandardClaim address = standardClaims.getAddress();
        apply(target::setPostalAddress, address == null ? null : address.getFormatted());
    }

    /**
     * Applies the first non-null value from the provided alternatives to the
     * setter.
     *
     * @param setter       The setter method to apply the value to.
     * @param alternatives The list of potential values in order of preference.
     */
    protected void apply(Consumer<String> setter, String... alternatives) {
        Stream.of(alternatives).filter(Objects::nonNull).findFirst().ifPresent(setter::accept);
    }

    protected @Override Logger logger() {
        return log;
    }

    @VisibleForTesting
    void applyProviderNonStandardClaims(OpenIdConnectCustomClaimsConfigProperties customProviderClaims,
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

/*
 * Copyright (C) 2024 by the geOrchestra PSC
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
package org.georchestra.gateway.security.sirene;

import java.text.Normalizer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.georchestra.gateway.orgresolvers.OrganizationNameResolver;
import org.georchestra.gateway.orgresolvers.ResolvedOrganization;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link OrganizationNameResolver} implementation that calls the French INSEE
 * SIRENE API to resolve organization names from SIRET numbers.
 * <p>
 * The API endpoint used is {@code GET {baseUrl}/siret/{siret}} with a Bearer
 * token for authentication.
 * </p>
 * <p>
 * Results are cached in-memory (keyed by SIRET) to avoid hitting the API rate
 * limit (30 requests/min on the free tier).
 * </p>
 *
 * <p>
 * The organization name is extracted from the JSON response field
 * {@code etablissement.uniteLegale.denominationUniteLegale}. For individual
 * enterprises where this field is null,
 * {@code prenomUsuelUniteLegale + " " + nomUsuelUniteLegale} is used instead.
 * </p>
 *
 * @see SireneApiConfigProperties
 */
@Slf4j(topic = "org.georchestra.gateway.security.sirene")
public class SireneOrganizationNameResolver implements OrganizationNameResolver {

    private static final int SHORT_NAME_MAX_LENGTH = 32;

    private final RestClient restClient;

    /**
     * Simple in-memory cache to avoid redundant API calls for the same SIRET. An
     * empty Optional means a previous lookup returned no result; the key's absence
     * means it has never been looked up.
     */
    private final ConcurrentHashMap<String, Optional<ResolvedOrganization>> cache = new ConcurrentHashMap<>();

    /**
     * Constructs a new resolver.
     *
     * @param restClient a pre-configured {@link RestClient} pointing at the SIRENE
     *                   API base URL, with authentication headers set
     */
    public SireneOrganizationNameResolver(@NonNull RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<ResolvedOrganization> resolve(String organizationIdentifier) {
        if (organizationIdentifier == null || organizationIdentifier.isBlank()) {
            return Optional.empty();
        }

        return cache.computeIfAbsent(organizationIdentifier, this::doResolve);
    }

    @SuppressWarnings("unchecked")
    private Optional<ResolvedOrganization> doResolve(String siret) {
        try {
            log.info("Resolving organization name from SIRENE API for SIRET: {}", siret);

            Map<String, Object> response = restClient.get().uri("/siret/{siret}", siret).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                        throw new HttpClientErrorException(res.getStatusCode(), res.getStatusText());
                    }).body(Map.class);

            if (response == null) {
                log.warn("SIRENE API returned null response for SIRET: {}", siret);
                return Optional.empty();
            }

            Map<String, Object> etablissement = (Map<String, Object>) response.get("etablissement");
            if (etablissement == null) {
                log.warn("SIRENE API response missing 'etablissement' for SIRET: {}", siret);
                return Optional.empty();
            }

            Map<String, Object> uniteLegale = (Map<String, Object>) etablissement.get("uniteLegale");
            if (uniteLegale == null) {
                log.warn("SIRENE API response missing 'uniteLegale' for SIRET: {}", siret);
                return Optional.empty();
            }

            String orgName = extractOrgName(uniteLegale);
            if (orgName == null || orgName.isBlank()) {
                log.warn("Could not extract organization name from SIRENE API response for SIRET: {}", siret);
                return Optional.empty();
            }

            String shortName = deriveShortName(orgName);
            log.info("Resolved SIRET {} to organization: '{}' (short: '{}')", siret, orgName, shortName);
            return Optional.of(new ResolvedOrganization(orgName, shortName));

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("SIRET {} not found in SIRENE API (404)", siret);
            return Optional.empty();
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("SIRENE API rate limit exceeded while resolving SIRET: {}", siret);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error calling SIRENE API for SIRET: {}", siret, e);
            return Optional.empty();
        }
    }

    /**
     * Extracts the organization name from the {@code uniteLegale} JSON object.
     * <p>
     * Uses {@code denominationUniteLegale} for legal entities. For individual
     * enterprises, falls back to
     * {@code prenomUsuelUniteLegale + " " + nomUsuelUniteLegale}.
     * </p>
     */
    private String extractOrgName(Map<String, Object> uniteLegale) {
        String denomination = (String) uniteLegale.get("denominationUniteLegale");
        if (denomination != null && !denomination.isBlank()) {
            return denomination.trim();
        }

        // Individual enterprise fallback
        String prenom = (String) uniteLegale.get("prenomUsuelUniteLegale");
        String nom = (String) uniteLegale.get("nomUsuelUniteLegale");
        if (nom != null && !nom.isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (prenom != null && !prenom.isBlank()) {
                sb.append(prenom.trim()).append(" ");
            }
            sb.append(nom.trim());
            return sb.toString();
        }

        return null;
    }

    /**
     * Derives a short name from the full organization name.
     * <p>
     * The short name is:
     * <ul>
     * <li>Uppercased</li>
     * <li>Unicode-normalized and stripped of diacritical marks</li>
     * <li>Whitespace and special characters removed</li>
     * <li>Truncated to 32 characters</li>
     * </ul>
     * </p>
     *
     * @param orgName the full organization name
     * @return the derived short name
     */
    static String deriveShortName(String orgName) {
        String upper = orgName.toUpperCase();

        // Normalize Unicode and remove diacritical marks
        String normalized = Normalizer.normalize(upper, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Remove all non-alphanumeric characters
        normalized = normalized.replaceAll("[^A-Z0-9]", "");

        // Truncate to max length
        if (normalized.length() > SHORT_NAME_MAX_LENGTH) {
            normalized = normalized.substring(0, SHORT_NAME_MAX_LENGTH);
        }

        return normalized;
    }
}

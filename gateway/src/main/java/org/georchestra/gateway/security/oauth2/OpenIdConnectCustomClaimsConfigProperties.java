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

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.georchestra.security.model.GeorchestraUser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@ConfigurationProperties(prefix = "georchestra.gateway.security.oidc.claims")
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public @Data class OpenIdConnectCustomClaimsConfigProperties {

    private JsonPathExtractor id = new JsonPathExtractor();
    private RolesMapping roles = new RolesMapping();
    private JsonPathExtractor organization = new JsonPathExtractor();
    private JsonPathExtractor organizationUid = new JsonPathExtractor();
    private JsonPathExtractor familyName = new JsonPathExtractor();
    private JsonPathExtractor givenName = new JsonPathExtractor();
    private JsonPathExtractor email = new JsonPathExtractor();

    private Map<String, OpenIdConnectCustomClaimsConfigProperties> provider = new HashMap<>();

    public Optional<JsonPathExtractor> id() {
        return Optional.ofNullable(id);
    }

    public Optional<RolesMapping> roles() {
        return Optional.ofNullable(roles);
    }

    public Optional<JsonPathExtractor> organization() {
        return Optional.ofNullable(organization);
    }

    public Optional<JsonPathExtractor> organizationUid() {
        return Optional.ofNullable(organizationUid);
    }

    public Optional<JsonPathExtractor> familyName() {
        return Optional.ofNullable(familyName);
    }

    public Optional<JsonPathExtractor> givenName() {
        return Optional.ofNullable(givenName);
    }

    public Optional<JsonPathExtractor> email() {
        return Optional.ofNullable(email);
    }

    public Optional<OpenIdConnectCustomClaimsConfigProperties> getProviderConfig(@NonNull String providerName) {
        return Optional.ofNullable(provider.get(providerName));
    }

    @Accessors(chain = true)
    public static @Data class RolesMapping {

        private JsonPathExtractor json = new JsonPathExtractor();

        /**
         * Whether to return mapped role names as upper-case
         */
        private boolean uppercase = true;

        /**
         * Whether to remove special characters and replace spaces by underscores
         */
        private boolean normalize = true;

        /**
         * Whether to append the resolved role names to the roles given by the OAuth2
         * authentication (true), or replace them (false).
         */
        private boolean append = true;

        public Optional<JsonPathExtractor> json() {
            return Optional.ofNullable(json);
        }

        public void apply(Map<String, Object> claims, GeorchestraUser target) {

            json().ifPresent(json -> {
                List<String> rawValues = json.extract(claims);
                List<String> roles = rawValues.stream().map(this::applyTransforms)
                        // make sure the resulting list is mutable, Stream.toList() is not
                        .collect(Collectors.toList());
                if (roles.isEmpty()) {
                    return;
                }
                if (append) {
                    target.getRoles().addAll(0, roles);
                } else {
                    target.setRoles(roles);
                }
            });
        }

        private String applyTransforms(String value) {
            String result = uppercase ? value.toUpperCase() : value;
            if (normalize) {
                result = normalize(result);
            }
            return result;
        }

        public String normalize(@NonNull String value) {
            // apply Unicode Normalization (NFC: a + ◌̂ = â) (see
            // https://www.unicode.org/reports/tr15/)
            String normalized = Normalizer.normalize(value, Form.NFC);

            // remove unicode accents and diacritics
            normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

            // replace all whitespace groups by a single underscore
            normalized = value.replaceAll("\\s+", "_");

            // remove remaining characters like parenthesis, commas, etc
            normalized = normalized.replaceAll("[^a-zA-Z0-9_]", "");
            return normalized;
        }
    }

    @Accessors(chain = true)
    public static @Data class JsonPathExtractor {
        /**
         * JsonPath expression(s) to extract the role names from the
         * {@literal Map<String, Object>} containing all OIDC authentication token
         * claims.
         * <p>
         * For example, if the claims map contains a JSON object under the
         * {@literal groups_json} key with the value
         * 
         * <pre>
         * {@code
         * [
         *     [
         *       {
         *         "name": "GDI FTTH Planer (extern)",
         *         "targetSystem": "gdiFibreAdmin",
         *         "parameter": []
         *       }
         *     ]
         *   ]
         * }
         * </pre>
         * 
         * The JsonPath expression {@literal $.groups_json[0][0].name} would match only
         * the first group name, while the expression {@literal $.groups_json..['name']}
         * would match them all to a {@code List<String>}.
         */
        private List<String> path = new ArrayList<>();

        public JsonPathExtractor() {
        }

        public JsonPathExtractor(List<String> path) {
            this.path = path;
        }

        /**
         * @param claims
         * @return
         */
        public @NonNull List<String> extract(@NonNull Map<String, Object> claims) {
            return this.path.stream()//
                    .map(jsonPathExpression -> this.extract(jsonPathExpression, claims))//
                    .flatMap(List::stream)//
                    .collect(Collectors.toList());
        }

        private List<String> extract(final String jsonPathExpression, Map<String, Object> claims) {
            if (!StringUtils.hasText(jsonPathExpression)) {
                return List.of();
            }

            // if we call claims.get(key) and the result is a JSON object,
            // the json api used is a shaded version of org.json at package
            // com.nimbusds.jose.shaded.json, we don't want to use that
            // since it's obviously internal to com.nimbusds.jose
            // JsonPath works fine with it though, as it's designed
            // to work on POJOS, JSONObject is a Map and JSONArray is a List so it's ok
            DocumentContext context = JsonPath.parse(claims);
            Object matched;

            try {
                matched = context.read(jsonPathExpression);
            } catch (PathNotFoundException e) {
                log.warn("JSONPath expression {} not found in claims", jsonPathExpression, e);
                return List.of();
            }

            if (null == matched) {
                log.warn("The JSONPath expession {} evaluates to null", jsonPathExpression);
                return List.of();
            }

            final List<?> list = (matched instanceof List) ? (List<?>) matched : List.of(matched);

            List<String> values = IntStream.range(0, list.size())//
                    .mapToObj(list::get)//
                    .filter(Objects::nonNull)//
                    .map(value -> validateValueIsString(jsonPathExpression, value))//
                    .collect(Collectors.toList());

            return values;
        }

        private String validateValueIsString(final String jsonPathExpression, @NonNull Object v) {
            if (v instanceof String)
                return (String) v;

            String msg = String.format("The JSONPath expression %s evaluates to %s instead of String. Value: %s",
                    jsonPathExpression, v.getClass().getCanonicalName(), v);
            throw new IllegalStateException(msg);

        }
    }
}

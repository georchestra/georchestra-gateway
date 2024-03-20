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
package org.georchestra.gateway.filter.headers.providers;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.georchestra.gateway.filter.headers.HeaderContributor;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.georchestra.commons.security.SecurityHeaders.*;

/**
 * Contributes user-related {@literal sec-*} request headers.
 * 
 * @see GeorchestraUsers#resolve
 * @see GeorchestraTargetConfig
 */
public class GeorchestraUserHeadersContributor extends HeaderContributor {

    public @Override Consumer<HttpHeaders> prepare(ServerWebExchange exchange) {
        return headers -> {
            GeorchestraTargetConfig.getTarget(exchange)//
                    .map(GeorchestraTargetConfig::headers)//
                    .ifPresent(mappings -> {
                        Optional<GeorchestraUser> user = GeorchestraUsers.resolve(exchange);
                        add(headers, SEC_USERID, mappings.getUserid(), user.map(GeorchestraUser::getId));
                        add(headers, SEC_USERNAME, mappings.getUsername(), user.map(GeorchestraUser::getUsername));
                        add(headers, SEC_ORG, mappings.getOrg(), user.map(GeorchestraUser::getOrganization));
                        add(headers, SEC_EMAIL, mappings.getEmail(), user.map(GeorchestraUser::getEmail));
                        add(headers, SEC_FIRSTNAME, mappings.getFirstname(), user.map(GeorchestraUser::getFirstName));
                        add(headers, SEC_LASTNAME, mappings.getLastname(), user.map(GeorchestraUser::getLastName));
                        add(headers, SEC_TEL, mappings.getTel(), user.map(GeorchestraUser::getTelephoneNumber));

                        List<String> roles = user.map(GeorchestraUser::getRoles).orElse(List.of());

                        add(headers, SEC_ROLES, mappings.getRoles(), roles);

                        add(headers, SEC_LASTUPDATED, mappings.getLastUpdated(),
                                user.map(GeorchestraUser::getLastUpdated));
                        add(headers, SEC_ADDRESS, mappings.getAddress(), user.map(GeorchestraUser::getPostalAddress));
                        add(headers, SEC_TITLE, mappings.getTitle(), user.map(GeorchestraUser::getTitle));
                        add(headers, SEC_NOTES, mappings.getNotes(), user.map(GeorchestraUser::getNotes));
                        add(headers, SEC_LDAP_REMAINING_DAYS, Optional
                                .of(user.isPresent() && user.get().getLdapWarn() != null && user.get().getLdapWarn()),
                                user.map(GeorchestraUser::getLdapRemainingDays));
                        add(headers, SEC_EXTERNAL_AUTHENTICATION, Optional.of(user.isPresent()),
                                String.valueOf(user.isPresent() && user.get().getIsExternalAuth()));
                    });
        };
    }
}

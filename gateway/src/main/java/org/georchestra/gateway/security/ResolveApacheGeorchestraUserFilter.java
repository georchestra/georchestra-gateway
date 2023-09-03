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
package org.georchestra.gateway.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.georchestra.ds.DataServiceException;
import org.georchestra.ds.roles.Role;
import org.georchestra.ds.roles.RoleDao;
import org.georchestra.ds.security.UserMapperImpl;
import org.georchestra.ds.security.UsersApiImpl;
import org.georchestra.ds.users.*;
import org.georchestra.gateway.model.GeorchestraTargetConfig;
import org.georchestra.gateway.model.GeorchestraUsers;
import org.georchestra.gateway.security.ldap.LdapConfigProperties;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link GlobalFilter} that resolves the {@link GeorchestraUser} from the
 * request's {@link Authentication} so it can be {@link GeorchestraUsers#resolve
 * retrieved} down the road during a server web exchange filter chain execution.
 * <p>
 * The resolved per-request {@link GeorchestraUser user} object can then, for
 * example, be used to append the necessary {@literal sec-*} headers that relate
 * to user information to proxied http requests.
 * 
 * @see GeorchestraUserMapper
 */
@RequiredArgsConstructor
@Slf4j(topic = "org.georchestra.gateway.security")
public class ResolveApacheGeorchestraUserFilter implements GlobalFilter, Ordered {

    @Autowired
    LdapConfigProperties config;

    @Autowired(required = false)
    private AccountDao accountDao;

    @Autowired(required = false)
    private RoleDao roleDao;

    public static final int ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 2;

    /**
     * @return a lower precedence than {@link RouteToRequestUrlFilter}'s, in order
     *         to make sure the matched {@link Route} has been set as a
     *         {@link ServerWebExchange#getAttributes attribute} when
     *         {@link #filter} is called.
     */
    public @Override int getOrder() {
        return ORDER;
    }

    /**
     * Resolves the matched {@link Route} and its corresponding
     * {@link GeorchestraTargetConfig}, if possible, and proceeds with the filter
     * chain.
     */
    public @Override Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getHeaders().containsKey("sec-mellon-name-id")) {
            GeorchestraUsers.store(exchange,
                    map(exchange.getRequest().getHeaders().get("sec-username").get(0)).orElse(null));
            return chain.filter(exchange);
        } else {
            return chain.filter(exchange);
        }
    }

    protected Optional<GeorchestraUser> map(String username) {
        UserMapperImpl mapper = new UserMapperImpl();
        mapper.setRoleDao(roleDao);
        List<String> protectedUsers = Collections.emptyList();
        UserRule rule = new UserRule();
        rule.setListOfprotectedUsers(protectedUsers.toArray(String[]::new));
        UsersApiImpl usersApi = new UsersApiImpl();
        usersApi.setAccountsDao(accountDao);
        usersApi.setMapper(mapper);
        usersApi.setUserRule(rule);

        Optional<GeorchestraUser> userOpt = usersApi.findByUsername(username);
        List<String> roles = userOpt.get().getRoles().stream().map(r -> r.contains("ROLE_") ? r : "ROLE_" + r)
                .collect(Collectors.toList());
        userOpt.get().setRoles(roles);
        return userOpt;

    }

}
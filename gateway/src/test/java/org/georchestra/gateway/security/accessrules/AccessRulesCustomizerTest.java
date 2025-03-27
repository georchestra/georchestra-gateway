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

package org.georchestra.gateway.security.accessrules;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.Map;

import org.georchestra.gateway.model.GatewayConfigProperties;
import org.georchestra.gateway.model.RoleBasedAccessRule;
import org.georchestra.gateway.model.Service;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;

/**
 * Test suite for {@link AccessRulesCustomizer}
 *
 */
class AccessRulesCustomizerTest {

    private GatewayConfigProperties config;
    private AccessRulesCustomizer customizer;
    private ServerHttpSecurity http;

    @BeforeEach
    void setUp() throws Exception {
        config = new GatewayConfigProperties();
        customizer = new AccessRulesCustomizer(config, new GeorchestraUserMapper(List.of(), List.of()));
        http = spy(new ServerHttpSecurity() {
        });
    }

    @Test
    void testConstructorDoesNotAcceptNullConfig() {
        assertThrows(NullPointerException.class,
                () -> new AccessRulesCustomizer(null, new GeorchestraUserMapper(List.of(), List.of())));
        assertThrows(NullPointerException.class, () -> new AccessRulesCustomizer(config, null));
    }

    @Test
    void testCustomize_empty_config() {
        customizer.customize(http);
        verify(http, atLeastOnce()).authorizeExchange();
        verifyNoMoreInteractions(http);
    }

    @Test
    void testCustomize_applies_service_rules_before_global_rules() {

        RoleBasedAccessRule global1 = mock(RoleBasedAccessRule.class);
        RoleBasedAccessRule global2 = mock(RoleBasedAccessRule.class);
        RoleBasedAccessRule service1Rule1 = mock(RoleBasedAccessRule.class);
        RoleBasedAccessRule service1Rule2 = mock(RoleBasedAccessRule.class);

        Service service1 = new Service();
        service1.setAccessRules(List.of(service1Rule1, service1Rule2));

        config.setGlobalAccessRules(List.of(global1, global2));
        config.setServices(Map.of("service1", service1));

        customizer = spy(customizer);

        ArgumentCaptor<RoleBasedAccessRule> ruleCaptor = ArgumentCaptor.forClass(RoleBasedAccessRule.class);

        doNothing().when(customizer).apply(any(), any());
        customizer.customize(http);
        verify(customizer, times(4)).apply(any(), ruleCaptor.capture());
        assertSame(service1Rule1, ruleCaptor.getAllValues().getFirst());
        assertSame(service1Rule2, ruleCaptor.getAllValues().get(1));
        assertSame(global1, ruleCaptor.getAllValues().get(2));
        assertSame(global2, ruleCaptor.getAllValues().get(3));
    }

    @Test
    void testApplyRule_EmptyInterceptUrls() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();
        RoleBasedAccessRule rule = rule().setAnonymous(true);

        assertThrows(IllegalArgumentException.class, () -> customizer.apply(spec, rule),
                "No ant-pattern(s) defined for rule");
    }

    @Test
    void testApplyRule_AuthorizeExchangeWithAntPatterns() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();

        RoleBasedAccessRule rule = rule("/test/**", "/page1");
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).authorizeExchange(same(spec), eq(List.of("/test/**", "/page1")));
    }

    @Test
    void testApplyRule_anonymous() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();

        RoleBasedAccessRule rule = rule("/test/**", "/page1").setAnonymous(true);
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).authorizeExchange(same(spec), eq(List.of("/test/**", "/page1")));
        verify(customizer, times(1)).permitAll(any());
    }

    @Test
    void testApplyRule_anonymous_has_precedence_over_roles_list() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();

        RoleBasedAccessRule rule = rule("/test/**", "/page1").setAnonymous(true).setAllowedRoles(List.of("ROLE_ADMIN"));
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).authorizeExchange(same(spec), eq(List.of("/test/**", "/page1")));
        verify(customizer, times(1)).permitAll(any());
        verify(customizer, times(0)).requireAuthenticatedUser(any());
        verify(customizer, times(0)).hasAnyAuthority(any(), any());
    }

    @Test
    void testApplyRule_authenticated() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();

        RoleBasedAccessRule rule = rule("/test/**", "/page1").setAnonymous(false);
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).authorizeExchange(same(spec), eq(List.of("/test/**", "/page1")));
        verify(customizer, times(1)).requireAuthenticatedUser(any());
    }

    @Test
    void testApplyRule_roles() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();

        List<String> roles = List.of("ROLE_ADMIN", "ROLE_TESTER");
        RoleBasedAccessRule rule = rule("/test/**", "/page1").setAllowedRoles(roles);
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).authorizeExchange(same(spec), eq(List.of("/test/**", "/page1")));
        verify(customizer, times(1)).hasAnyAuthority(any(), eq(roles));
    }

    @Test
    void testApplyRule_roles_prefix_added_if_missing() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();

        List<String> roles = List.of("ADMIN", "TESTER");
        List<String> expected = List.of("ROLE_ADMIN", "ROLE_TESTER");
        RoleBasedAccessRule rule = rule("/test/**", "/page1").setAllowedRoles(roles);
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).authorizeExchange(same(spec), eq(List.of("/test/**", "/page1")));
        verify(customizer, times(1)).hasAnyAuthority(any(), eq(expected));
    }

    @Test
    void testApplyRule_forbidden() {
        AuthorizeExchangeSpec spec = http.authorizeExchange();
        RoleBasedAccessRule rule = rule("/test/**", "/page1").setForbidden(true);
        customizer = spy(customizer);
        customizer.apply(spec, rule);

        verify(customizer, times(1)).denyAll(any());
        verify(customizer, never()).requireAuthenticatedUser(any());
        verify(customizer, never()).hasAnyAuthority(any(), any());
        verify(customizer, never()).permitAll(any());
    }

    private RoleBasedAccessRule rule(String... interceptUrls) {
        RoleBasedAccessRule rule = new RoleBasedAccessRule();
        rule.setInterceptUrl(List.of(interceptUrls));
        return rule;
    }
}

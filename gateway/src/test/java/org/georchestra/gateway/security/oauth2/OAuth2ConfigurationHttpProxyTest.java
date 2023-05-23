/*
 * Copyright (C) 2021 by the geOrchestra PSC
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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.georchestra.gateway.security.oauth2.OAuth2Configuration.OAuth2AuthenticationCustomizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ClientSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.Getter;
import lombok.NonNull;

@SpringBootTest(classes = { GeorchestraGatewayApplication.class,
        OAuth2ConfigurationHttpProxyTest.ServerHttpSecurityCapturingConfiguration.class }, properties = {
                "georchestra.datadir=src/test/resources/test-datadir",
                "georchestra.gateway.security.oauth2.enabled: true",
                "spring.security.oauth2.client.registration.google.clientId: fale_client_id",
                "spring.security.oauth2.client.registration.google.clientSecret: fake_secret" })
@ActiveProfiles({ "test" })
class OAuth2ConfigurationHttpProxyTest {

    private @Autowired ApplicationContext context;
    private @Autowired @Qualifier("oauth2WebClient") WebClient proxyWebClient;

    private @Autowired SecurityWebFilterChain securityWebFilterChain;

    /**
     * ServerHttpSecurity is a prototype bean, so we use this capturer to get the
     * same instance as {@link OAuth2AuthenticationCustomizer}
     */
    private @Autowired ServerHttpSecurityCapturingConfiguration capturer;

    @Configuration
    static class ServerHttpSecurityCapturingConfiguration {

        private @Getter ServerHttpSecurity serverHttpSecurity;

        @Bean
        public ServerHttpSecurityCustomizer capturer() {
            return new ServerHttpSecurityCustomizer() {
                public @Override int getOrder() {
                    return Ordered.LOWEST_PRECEDENCE;
                }

                public @Override void customize(ServerHttpSecurity http) {
                    serverHttpSecurity = http;
                }
            };
        }
    }

    private ServerHttpSecurity serverHttpSecurity;

    public @BeforeEach void setup() {
        serverHttpSecurity = capturer.getServerHttpSecurity();
    }

    public @Test void testOauth2LoginWebClients() throws Exception {
        OAuth2LoginSpec oauth2Login = serverHttpSecurity.oauth2Login();
        verifyProxyAwareWebClients("oauth2Login", oauth2Login);
    }

    public @Test void testReactiveOAuth2AuthorizedClientManager() throws Exception {
        ReactiveOAuth2AuthorizedClientManager manager = context.getBean(ReactiveOAuth2AuthorizedClientManager.class);
        verifyProxyAwareWebClients("ReactiveOAuth2AuthorizedClientManager", manager);
    }

    public @Test void testOauth2ClientWebClients() throws Exception {
        OAuth2ClientSpec oauth2Client = serverHttpSecurity.oauth2Client();
        verifyProxyAwareWebClients("oauth2Client", oauth2Client);
    }

    public @Test void testAllServerHttpSecurityWebClients() throws Exception {
        verifyProxyAwareWebClients("serverHttpSecurity", serverHttpSecurity);
    }

    private void verifyProxyAwareWebClients(String propName, Object object) throws Exception {

        Map<String, WebClient> clients = findWebClientsRecursively(propName, object);
        clients.forEach((prop, client) -> {
            System.err.printf("%s: %s%n", prop, client);
            assertThat(client).as(prop + " is not a proxy aware client").isInstanceOf(ProxyAwareWebClient.class);
        });
    }

    private Map<String, WebClient> findWebClientsRecursively(String propertyName, @NonNull Object property)
            throws Exception {

        Map<String, Object> propGraph = new LinkedHashMap<>();

        depthSearchChildren(propertyName, property,
                (nesterPropName, nestedProp) -> propGraph.put(nesterPropName, nestedProp));

        return propGraph.entrySet().stream()
                .filter(e -> WebClient.class.isInstance(e.getValue()) && !e.getKey().endsWith(".delegate"))
                .collect(Collectors.toMap(e -> e.getKey(), e -> (WebClient) e.getValue()));
    }

    private void depthSearchChildren(String objectProperty, Object object, BiConsumer<String, Object> visitor)
            throws Exception {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(object);
        depthSearchChildren(objectProperty, object, visitor, visited);
    }

    private void depthSearchChildren(String objectProperty, @NonNull Object object, BiConsumer<String, Object> visitor,
            Set<Object> visited) throws Exception {

        if (object instanceof Iterable) {
            Iterable<?> it = (Iterable<?>) object;
            int i = 0;
            for (Iterator<?> iterator = it.iterator(); iterator.hasNext(); i++) {
                String propName = objectProperty + "[" + i + "]";
                Object value = iterator.next();
                visitor.accept(propName, value);

                if (value != null) {
                    if (visited.contains(value))
                        continue;
                    visited.add(value);
                }

                if (value != null && !value.getClass().isPrimitive()
                        && !value.getClass().getName().startsWith("java.lang")) {
                    depthSearchChildren(propName, value, visitor, visited);
                }
            }
            return;
        }

        HierarchyTraversalMode mode = HierarchyTraversalMode.BOTTOM_UP;
        List<Field> fields = ReflectionSupport.findFields(object.getClass(), field -> !field.getType().isPrimitive()
                && !field.getType().isEnum() && !field.getType().getName().startsWith("java.lang"), mode);

        for (Field field : fields) {
            final boolean isStatic = (field.getModifiers() & Modifier.STATIC) != 0;
            if (isStatic)
                continue;
            String name = field.getName();
            try {
                field.setAccessible(true);
            } catch (InaccessibleObjectException e) {
                continue;
            }

            String propName = objectProperty + "." + name;
            Object value = field.get(object);
            Class<?> type = field.getType();
            visitor.accept(propName, value);

            if (value != null) {
                if (visited.contains(value))
                    continue;
                visited.add(value);
            }

            if (value != null && !type.isPrimitive() && !type.getName().startsWith("java.lang")) {
                depthSearchChildren(propName, value, visitor, visited);
            }
        }
    }

}

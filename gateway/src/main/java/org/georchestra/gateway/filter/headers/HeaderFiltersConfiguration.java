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
package org.georchestra.gateway.filter.headers;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.georchestra.gateway.filter.headers.providers.GeorchestraOrganizationHeadersContributor;
import org.georchestra.gateway.filter.headers.providers.GeorchestraUserHeadersContributor;
import org.georchestra.gateway.filter.headers.providers.JsonPayloadHeadersContributor;
import org.georchestra.gateway.filter.headers.providers.SecProxyHeaderContributor;
import org.georchestra.gateway.model.GatewayConfigProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayConfigProperties.class)
public class HeaderFiltersConfiguration {

    /**
     * {@link GatewayFilterFactory} to add all necessary {@literal sec-*} request
     * headers to proxied requests.
     * 
     * @param providers the list of configured {@link HeaderContributor}s in the
     *                  {@link ApplicationContext}
     * @see #secProxyHeaderProvider()
     * @see #userSecurityHeadersProvider()
     * @see #organizationSecurityHeadersProvider()
     */

    @Bean
    AddSecHeadersGatewayFilterFactory addSecHeadersGatewayFilterFactory(List<HeaderContributor> providers) {
        return new AddSecHeadersGatewayFilterFactory(providers);
    }

    @Bean
    CookieAffinityGatewayFilterFactory cookieAffinityGatewayFilterFactory() {
        return new CookieAffinityGatewayFilterFactory();
    }

    @Bean
    GeorchestraUserHeadersContributor userSecurityHeadersProvider() {
        return new GeorchestraUserHeadersContributor();
    }

    @Bean
    SecProxyHeaderContributor secProxyHeaderProvider(GatewayConfigProperties configProps) {
        BooleanSupplier secProxyEnabledSupplier = () -> configProps.getDefaultHeaders().getProxy().orElse(false);
        return new SecProxyHeaderContributor(secProxyEnabledSupplier);
    }

    @Bean
    GeorchestraOrganizationHeadersContributor organizationSecurityHeadersProvider() {
        return new GeorchestraOrganizationHeadersContributor();
    }

    @Bean
    JsonPayloadHeadersContributor jsonPayloadHeadersContributor() {
        return new JsonPayloadHeadersContributor();
    }

    /**
     * General purpose {@link GatewayFilterFactory} to remove incoming HTTP request
     * headers based on a Java regular expression
     */
    @Bean
    RemoveHeadersGatewayFilterFactory removeHeadersGatewayFilterFactory() {
        return new RemoveHeadersGatewayFilterFactory();
    }

    /**
     * {@link GatewayFilterFactory} to remove incoming HTTP {@literal sec-*} HTTP
     * request headers to prevent impersonation from outside
     */
    @Bean
    RemoveSecurityHeadersGatewayFilterFactory removeSecurityHeadersGatewayFilterFactory() {
        return new RemoveSecurityHeadersGatewayFilterFactory();
    }

}

/*
 * Copyright (C) 2023 by the geOrchestra PSC
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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.gateway.security.MockUserGeorchestraUserMapperConfiguration;
import org.georchestra.gateway.test.context.support.WithMockOidcUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import reactor.core.publisher.Mono;

/**
 * Verify that {@link OpenIdConnectClaimsHeaderContributor} appends the
 * {@link OidcUser} claims as request headers to downstream services
 */
@SpringBootTest(classes = { //
        GeorchestraGatewayApplication.class, //
        MockUserGeorchestraUserMapperConfiguration.class, //
        OpenIdConnectClaimsHeaderContributorIT.ForceOidcUserMapperBean.class//
}, webEnvironment = WebEnvironment.MOCK, properties = { "georchestra.datadir=../datadir", //
        "georchestra.gateway.security.ldap.default.enabled=false", //
        "georchestra.gateway.security.oauth2.enabled=false",//
})
@ActiveProfiles("it")
@AutoConfigureWebTestClient(timeout = "PT120S")
class OpenIdConnectClaimsHeaderContributorIT {

    @RegisterExtension
    static WireMockExtension mockService = WireMockExtension.newInstance()
            .options(new WireMockConfiguration().dynamicPort().dynamicHttpsPort()).build();

    /**
     * Bypass {@link OAuth2Configuration} since
     * {@code georchestra.gateway.security.oauth2.enabled=false} but contribute
     * {@link OAuth2ConfigurationProperties} and {@link OpenIdConnectUserMapper} to
     * resolve tokens from {@link WithMockOidcUser @WithMockOidcUser}.
     */
    @Configuration
    @EnableConfigurationProperties({ OAuth2ConfigurationProperties.class })
    static class ForceOidcUserMapperBean {

        @Bean
        OpenIdConnectClaimsHeaderContributor claimsHeaderContributor() {
            return new OpenIdConnectClaimsHeaderContributor();
        }

        @Bean
        OAuth2UserMapper oAuth2GeorchestraUserUserMapper(OAuth2ConfigurationProperties config) {
            return new OAuth2UserMapper(config);
        }

        @Bean
        OpenIdConnectUserMapper openIdConnectGeorchestraUserUserMapper(OAuth2ConfigurationProperties config) {
            return new OpenIdConnectUserMapper(config);
        }
    }

    /**
     * Configure the target service mappings to call the {@link #mockService} at its
     * dynamically allocated port
     * 
     * @see #mockServiceTarget
     */
    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        mockServiceTarget(registry, "header", "/header");
    }

    private static void mockServiceTarget(DynamicPropertyRegistry registry, String serviceName, String targetBaseURI) {
        WireMockRuntimeInfo runtimeInfo = mockService.getRuntimeInfo();
        String httpBaseUrl = runtimeInfo.getHttpBaseUrl();
        String proxiedURI = URI.create(httpBaseUrl + "/" + targetBaseURI).normalize().toString();
        String propertyName = String.format("georchestra.gateway.services.%s.target", serviceName);
        registry.add(propertyName, () -> proxiedURI);
    }

    @Autowired
    private WebTestClient testClient;

    @MockBean
    private ReactiveOAuth2AuthorizedClientManager oauth2ClientManager;

    @WithMockOidcUser(claims = { IdTokenClaimNames.AZP + "=test-party", IdTokenClaimNames.AT_HASH + "=test-hash" })
    @Test
    void oidc_provided_authorities_are_normalized_and_prefixed() {
        when(oauth2ClientManager.authorize(any())).thenReturn(Mono.empty());

        mockService.stubFor(get(urlMatching("/header(/.*)?")).willReturn(ok()));

        testClient.get().uri("/header").exchange()//
                .expectStatus().isOk();

        List<ServeEvent> allServeEvents = mockService.getAllServeEvents();
        HttpHeaders headers = allServeEvents.get(0).getRequest().getHeaders();

        HttpHeader azp = headers.getHeader("authorizedParty");
        assertThat(azp.isPresent()).isTrue();
        assertThat(azp.firstValue()).isEqualTo("test-party");

        HttpHeader atHash = headers.getHeader("accessTokenHash");
        assertThat(atHash.isPresent()).isTrue();
        assertThat(atHash.firstValue()).isEqualTo("test-hash");
    }
}

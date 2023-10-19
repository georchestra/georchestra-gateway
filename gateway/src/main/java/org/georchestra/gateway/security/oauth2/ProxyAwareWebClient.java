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

import org.georchestra.gateway.security.oauth2.OAuth2ConfigurationProperties.OAuth2ProxyConfigProperties;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Slf4j
class ProxyAwareWebClient implements WebClient {

    @Delegate
    private final @NonNull WebClient delegate;

    public ProxyAwareWebClient(@NonNull OAuth2ProxyConfigProperties proxyConfig) {
        final String proxyHost = proxyConfig.getHost();
        final Integer proxyPort = proxyConfig.getPort();
        final String proxyUser = proxyConfig.getUsername();
        final String proxyPassword = proxyConfig.getPassword();

        HttpClient httpClient = HttpClient.create();
        if (proxyConfig.isEnabled()) {
            if (proxyHost == null || proxyPort == null) {
                throw new IllegalStateException("OAuth2 client HTTP proxy is enabled, but host and port not provided");
            }
            log.info("Oauth2 client will use HTTP proxy {}:{}", proxyHost, proxyPort);
            httpClient = httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort)
                    .username(proxyUser).password(user -> {
                        return proxyPassword;
                    }));
        } else {
            log.info("Oauth2 client will use HTTP proxy from System properties if provided");
            httpClient = httpClient.proxyWithSystemProperties();
        }

        httpClient = httpClient
                .doOnRequest((req, conn) -> log.info("Performing proxied request to {}", req.resourceUrl()))
                .doOnResponse(
                        (resp, conn) -> log.info("Proxied response: {}, url: {}", resp.status(), resp.resourceUrl()));

        // httpClient = httpClient.wiretap(true);
        ReactorClientHttpConnector conn = new ReactorClientHttpConnector(httpClient);

        WebClient webClient = WebClient.builder().clientConnector(conn).build();
        this.delegate = webClient;
    }
}
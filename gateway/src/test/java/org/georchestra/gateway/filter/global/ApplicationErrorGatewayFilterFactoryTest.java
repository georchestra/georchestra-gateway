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
package org.georchestra.gateway.filter.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

/**
 * Tests for ApplicationErrorGatewayFilterFactory with a focused approach using
 * mocks rather than a full application context
 */
class ApplicationErrorGatewayFilterFactoryTest {

    private ApplicationErrorGatewayFilterFactory factory;
    private ServerHttpRequest request;
    private ServerWebExchange exchange;
    private ServerHttpResponse response;

    @BeforeEach
    void setUp() {
        factory = Mockito.spy(new ApplicationErrorGatewayFilterFactory());
        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
        exchange = mock(ServerWebExchange.class);

        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
    }

    @Test
    void testCanFilterFalseWhenNonIdempotentMethod() {
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        when(request.getHeaders()).thenReturn(headers);

        boolean result = factory.canFilter(request);

        assertThat(result).isFalse();
    }

    @Test
    void testCanFilterFalseWhenNotAcceptingHtml() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        when(request.getHeaders()).thenReturn(headers);

        boolean result = factory.canFilter(request);

        assertThat(result).isFalse();
    }

    @Test
    void testCanFilterTrueForIdempotentMethodAndHtmlAccept() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        when(request.getHeaders()).thenReturn(headers);

        boolean result = factory.canFilter(request);

        assertThat(result).isTrue();
    }

    @Test
    void testDecorateExchange() {
        // Use a real MockServerWebExchange instead of a mock to avoid delegate errors
        MockServerHttpRequest mockRequest = MockServerHttpRequest.get("/test").accept(MediaType.TEXT_HTML).build();
        MockServerWebExchange mockExchange = MockServerWebExchange.from(mockRequest);

        ServerWebExchange decorated = factory.decorate(mockExchange);

        assertThat(decorated).isNotNull();
        assertThat(decorated).isNotSameAs(mockExchange);
    }

    @Test
    void testMethodIsIdempotent() {
        assertThat(factory.methodIsIdempotent(HttpMethod.GET)).isTrue();
        assertThat(factory.methodIsIdempotent(HttpMethod.HEAD)).isTrue();
        assertThat(factory.methodIsIdempotent(HttpMethod.OPTIONS)).isTrue();
        assertThat(factory.methodIsIdempotent(HttpMethod.TRACE)).isTrue();

        assertThat(factory.methodIsIdempotent(HttpMethod.POST)).isFalse();
        assertThat(factory.methodIsIdempotent(HttpMethod.PUT)).isFalse();
        assertThat(factory.methodIsIdempotent(HttpMethod.PATCH)).isFalse();
        assertThat(factory.methodIsIdempotent(HttpMethod.DELETE)).isFalse();
    }

    @Test
    void testAcceptsHtml() {
        // Test with TEXT_HTML accept header
        HttpHeaders htmlHeaders = new HttpHeaders();
        htmlHeaders.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        ServerHttpRequest htmlRequest = mock(ServerHttpRequest.class);
        when(htmlRequest.getHeaders()).thenReturn(htmlHeaders);

        assertThat(factory.acceptsHtml(htmlRequest)).isTrue();

        // Test with non-HTML accept header
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ServerHttpRequest jsonRequest = mock(ServerHttpRequest.class);
        when(jsonRequest.getHeaders()).thenReturn(jsonHeaders);

        assertThat(factory.acceptsHtml(jsonRequest)).isFalse();

        // Test with multiple accept headers including HTML
        HttpHeaders multiHeaders = new HttpHeaders();
        multiHeaders.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML));
        ServerHttpRequest multiRequest = mock(ServerHttpRequest.class);
        when(multiRequest.getHeaders()).thenReturn(multiHeaders);

        assertThat(factory.acceptsHtml(multiRequest)).isTrue();
    }
}

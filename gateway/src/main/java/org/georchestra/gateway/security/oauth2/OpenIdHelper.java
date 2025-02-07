package org.georchestra.gateway.security.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class OpenIdHelper {

    /**
     * Utility function to decode JWT with Nimbus and read claims.
     * 
     * @param jwt
     * @return
     */
    public static Map<String, Object> decodeJwt(String jwt) {
        try {
            // Parse and decode the JWT using Nimbus
            JWT parsedJwt = JWTParser.parse(jwt);
            return parsedJwt.getJWTClaimsSet().getClaims();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to decode JWT", e);
        }
    }

    // Spring does not support application/jwt response. This allow to intercept
    // client response and transform body content from JWT to JSON.
    // More details: https://github.com/georchestra/georchestra-gateway/issues/168
    public static ExchangeFilterFunction transformJWTClientResponseToJSON() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.headers().contentType().isPresent()
                    && clientResponse.headers().contentType().get().toString().startsWith("application/jwt")) {
                return clientResponse.bodyToMono(String.class).flatMap(jwt -> {
                    try {
                        // Decode JWT to JSON
                        Map<String, Object> claims = decodeJwt(jwt);
                        // JSON to String
                        String json = new ObjectMapper().writeValueAsString(claims);

                        // Replace body by a valid JSON
                        return Mono.just(clientResponse.mutate()
                                .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_JSON))
                                .body(Flux.just(
                                        new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8))))
                                .build());
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to decode JWT", e));
                    }
                });
            }
            return Mono.just(clientResponse);
        });
    }

}
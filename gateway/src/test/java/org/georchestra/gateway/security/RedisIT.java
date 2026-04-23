package org.georchestra.gateway.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.georchestra.gateway.app.GeorchestraGatewayApplication;
import org.georchestra.testcontainers.ldap.GeorchestraLdapContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = GeorchestraGatewayApplication.class, properties = { "spring.session.redis.enabled=true",
        "spring.session.store-type=redis", "spring.data.redis.repositories.enabled=false" })
@AutoConfigureWebTestClient(timeout = "PT200S")
@ActiveProfiles("georheaders")
public class RedisIT {

    public static GeorchestraLdapContainer ldap = new GeorchestraLdapContainer();

    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    public static GenericContainer<?> httpEcho = new GenericContainer<>(DockerImageName.parse("ealen/echo-server"))
            .withExposedPorts(80);

    @Autowired
    private WebTestClient testClient;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.session.redis.host", redis::getHost);
        registry.add("spring.session.redis.port", redis::getFirstMappedPort);
        registry.add("httpEchoHost", httpEcho::getHost);
        registry.add("httpEchoPort", () -> httpEcho.getMappedPort(80));
        registry.add("ldapHost", ldap::getHost);
        registry.add("ldapPort", () -> ldap.getMappedPort(389));
        registry.add("ldapScheme", () -> "ldap");
    }

    @BeforeAll
    public static void startUpContainers() {
        ldap.start();
        redis.start();
        httpEcho.start();
    }

    @AfterAll
    public static void shutDownContainers() {
        ldap.stop();
        redis.stop();
        httpEcho.stop();
    }

    @BeforeEach
    public void emptyRedis() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void testRegularLogin() {
        // Initially Redis should be empty or at least no sessions
        Set<String> keysBefore = redisTemplate.keys("spring:session:*");
        assertTrue(keysBefore.isEmpty(), "Redis should not have sessions before login");

        // Perform login via form to ensure session creation
        testClient.post().uri("/login").contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("username", "testadmin") //
                        .with("password", "testadmin")) //
                .exchange() //
                .expectStatus() //
                .is3xxRedirection() //
                .expectHeader()//
                .exists("Set-Cookie").expectHeader().location("/");

        // Verify Redis has session keys
        Set<String> keysAfter = redisTemplate.keys("spring:session:*");
        assertFalse(keysAfter.isEmpty(), "Redis should have session keys after login");

        // Verify that the keys follow the Spring Session pattern
        boolean hasSessionData = keysAfter.stream().anyMatch(k -> k.contains(":sessions:"));
        assertTrue(hasSessionData, "Redis should contain session data keys");
    }

    @Test
    void testBasicAuthentication() {
        testClient.get().uri("/echo/").header("Authorization", "Basic dGVzdGFkbWluOnRlc3RhZG1pbg==") // testadmin:testadmin
                .header("Host", "localhost") // mandatory
                .exchange() //
                .expectStatus() //
                .is2xxSuccessful() //
                .expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.request.headers.sec-username")
                .isEqualTo("testadmin");

        // no session keys being set because B-A are stateless & the /echo/ endpoint
        // does not store anything in session.
        Set<String> keysAfter = redisTemplate.keys("spring:session:*");
        assertTrue(keysAfter.isEmpty(), "no session should have been created via basic-authentication");
    }

}

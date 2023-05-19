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
package org.georchestra.gateway.app;

import lombok.extern.slf4j.Slf4j;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.ldap.LdapConfigProperties;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Controller
@Slf4j
@SpringBootApplication
public class GeorchestraGatewayApplication {

    private @Autowired RouteLocator routeLocator;
    private @Autowired GeorchestraUserMapper userMapper;

    private @Autowired(required = false) LdapConfigProperties ldapConfigProperties;
    private @Autowired(required = false) OAuth2ClientProperties oauth2ClientConfig;
    private boolean ldapEnabled = false;
    private @Value("${georchestra.gateway.headerUrl:/header/}") String georchestraHeaderUrl;
    private @Value("${georchestra.gateway.headerHeight:90}") String georchestraHeaderHeight;
    private @Value("${georchestra.gateway.footerUrl:#{null}}") String georchestraFooterUrl;

    @PostConstruct
    void initialize() {
        if (ldapConfigProperties != null) {
            ldapEnabled = ldapConfigProperties.getLdap().values().stream().anyMatch((server -> server.isEnabled()));
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(GeorchestraGatewayApplication.class, args);
    }

    @GetMapping(path = "/whoami", produces = "application/json")
    @ResponseBody
    public Mono<Map<String, Object>> whoami(Authentication principal, ServerWebExchange exchange) {
        GeorchestraUser user = Optional.ofNullable(principal).flatMap(userMapper::resolve).orElse(null);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("GeorchestraUser", user);
        if (principal == null) {
            ret.put("Authentication", null);
        } else {
            ret.put(principal.getClass().getCanonicalName(), principal);
        }
        return Mono.just(ret);
        // return principal == null ? Mono.empty() :
        // Mono.just(Map.of(principal.getClass().getCanonicalName(), principal));
    }

    @GetMapping(path = "/login")
    public String loginPage(Model mdl) {
        Map<String, String> oauth2LoginLinks = new HashMap<String, String>();
        if (oauth2ClientConfig != null) {
            oauth2ClientConfig.getRegistration().forEach((k, v) -> {
                oauth2LoginLinks.put("/oauth2/authorization/" + k, v.getClientName());
            });
        }
        mdl.addAttribute("header_url", georchestraHeaderUrl);
        mdl.addAttribute("header_height", georchestraHeaderHeight);
        mdl.addAttribute("footer_url", georchestraFooterUrl);
        mdl.addAttribute("ldapEnabled", ldapEnabled);
        mdl.addAttribute("oauth2LoginLinks", oauth2LoginLinks);

        return "login";
    }

    @GetMapping(path = "/logout", produces = "text/html")
    public Mono<Rendering.Builder<?>> logout(Authentication principal, ServerWebExchange exchange) {
        return Mono.just(Rendering.view("logout"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent e) {
        Environment env = e.getApplicationContext().getEnvironment();
        String datadir = env.getProperty("georchestra.datadir");
        if (null != datadir) {
            datadir = new File(datadir).getAbsolutePath();
        }
        String app = env.getProperty("spring.application.name");
        String instanceId = env.getProperty("info.instance-id");
        int cpus = Runtime.getRuntime().availableProcessors();
        String maxMem;
        {
            DataSize maxMemBytes = DataSize.ofBytes(Runtime.getRuntime().maxMemory());
            double value = maxMemBytes.toKilobytes() / 1024d;
            String unit = "MB";
            if (maxMemBytes.toGigabytes() > 0) {
                value = value / 1024d;
                unit = "GB";
            }
            maxMem = String.format("%.2f %s", value, unit);
        }
        Long routeCount = routeLocator.getRoutes().count().block();
        log.info("{} ready. Data dir: {}. Routes: {}. Instance-id: {}, cpus: {}, max memory: {}", app, datadir,
                routeCount, instanceId, cpus, maxMem);
    }

}

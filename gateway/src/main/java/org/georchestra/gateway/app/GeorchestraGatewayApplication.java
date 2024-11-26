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
import org.apache.commons.lang3.tuple.Pair;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties.Server;
import org.georchestra.gateway.security.GeorchestraUserMapper;
import org.georchestra.gateway.security.exceptions.DuplicatedEmailFoundException;
import org.georchestra.security.model.GeorchestraUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(GeorchestraGatewaySecurityConfigProperties.class)
public class GeorchestraGatewayApplication {

    private @Autowired RouteLocator routeLocator;
    private @Autowired GeorchestraUserMapper userMapper;

    private @Autowired(required = false) GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties;

    private boolean ldapEnabled = false;

    private @Autowired(required = false) OAuth2ClientProperties oauth2ClientConfig;
    private @Value("${georchestra.gateway.headerEnabled:true}") boolean headerEnabled;

    // defined in georchestra datadir's default.properties
    private @Value("${georchestraStylesheet:}") String georchestraStylesheet;
    private @Value("${useLegacyHeader:false}") boolean useLegacyHeader;
    private @Value("${headerUrl:/header/}") String headerUrl;
    private @Value("${headerHeight:90}") int headerHeight;
    private @Value("${logoUrl:}") String logoUrl;
    private @Value("${headerScript:https://cdn.jsdelivr.net/gh/georchestra/header@dist/header.js}") String headerScript;
    private @Value("${spring.messages.basename:}") String messagesBasename;

    public static void main(String[] args) {
        SpringApplication.run(GeorchestraGatewayApplication.class, args);
    }

    @PostConstruct
    void initialize() {
        if (georchestraGatewaySecurityConfigProperties != null) {
            ldapEnabled = georchestraGatewaySecurityConfigProperties.getLdap().values().stream()
                    .anyMatch((Server::isEnabled));
        }
    }

    @GetMapping(path = "/whoami", produces = "application/json")
    @ResponseBody
    public Mono<Map<String, Object>> whoami(Authentication principal, ServerWebExchange exchange) {
        GeorchestraUser user;
        try {
            user = Optional.ofNullable(principal).flatMap(userMapper::resolve).orElse(null);
        } catch (DuplicatedEmailFoundException e) {
            user = null;
        }

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("GeorchestraUser", user);
        if (principal == null) {
            ret.put("Authentication", null);
        } else {
            ret.put(principal.getClass().getCanonicalName(), principal);
        }
        return Mono.just(ret);
    }

    @GetMapping(path = "/logout")
    public String logout(Model mdl) {
        setHeaderAttributes(mdl);
        return "logout";
    }

    @GetMapping(path = "/login")
    public String loginPage(@RequestParam Map<String, String> allRequestParams, Model mdl) {
        Map<String, Pair<String, String>> oauth2LoginLinks = new HashMap<>();
        if (oauth2ClientConfig != null) {
            oauth2ClientConfig.getRegistration().forEach((k, v) -> {
                String clientName = Optional.ofNullable(v.getClientName()).orElse(k);

                String providerPath = Paths.get("login/img/", k + ".png").toString();
                String logo = new ClassPathResource("static/" + providerPath).exists() ? providerPath
                        : "login/img/default.png";
                oauth2LoginLinks.put("/oauth2/authorization/" + k, Pair.of(clientName, logo));
            });
        }

        if (oauth2LoginLinks.size() == 1 && !ldapEnabled) {
            return "redirect:" + oauth2LoginLinks.keySet().stream().findFirst().get();
        }

        setHeaderAttributes(mdl);
        mdl.addAttribute("ldapEnabled", ldapEnabled);
        mdl.addAttribute("oauth2LoginLinks", oauth2LoginLinks);
        boolean expired = "expired_password".equals(allRequestParams.get("error"));
        mdl.addAttribute("passwordExpired", expired);
        boolean invalidCredentials = "invalid_credentials".equals(allRequestParams.get("error"));
        mdl.addAttribute("invalidCredentials", invalidCredentials);
        boolean duplicateAccount = "duplicate_account".equals(allRequestParams.get("error"));
        mdl.addAttribute("duplicateAccount", duplicateAccount);
        return "login";
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

    /**
     * REVISIT: why do we need to define this bean in the Application class and not
     * in a configuration that depends on whether rabbit is enabled?
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(("classpath:messages/login," + messagesBasename).split(","));
        messageSource.setCacheSeconds(600);
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return messageSource;
    }

    private void setHeaderAttributes(Model mdl) {
        mdl.addAttribute("georchestraStylesheet", georchestraStylesheet);
        mdl.addAttribute("useLegacyHeader", useLegacyHeader);
        mdl.addAttribute("headerUrl", headerUrl);
        mdl.addAttribute("headerHeight", headerHeight);
        mdl.addAttribute("logoUrl", logoUrl);
        mdl.addAttribute("headerEnabled", headerEnabled);
        mdl.addAttribute("headerScript", headerScript);
    }
}

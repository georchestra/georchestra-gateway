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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra. If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.app;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.tuple.Pair;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties;
import org.georchestra.gateway.security.GeorchestraGatewaySecurityConfigProperties.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller handling login and logout views for the geOrchestra gateway.
 * <p>
 * This controller serves the login and logout pages, manages authentication
 * options, and provides necessary attributes for rendering login-related
 * templates.
 * </p>
 *
 * <p>
 * It supports authentication through:
 * <ul>
 * <li>LDAP (if enabled via configuration)</li>
 * <li>OAuth2 providers registered in Spring Security</li>
 * <li>Header-based authentication</li>
 * </ul>
 * </p>
 */
// We have to use the @Controller annotation, not the @RestController one
// here, so that the login/logout page will go through the thymeleaf templating
// system.
@Controller
public class LoginLogoutController {

    /** Configuration properties for gateway security, including LDAP settings. */
    private @Autowired(required = false) GeorchestraGatewaySecurityConfigProperties georchestraGatewaySecurityConfigProperties;

    /** Whether LDAP authentication is enabled. */
    private boolean ldapEnabled;

    /** OAuth2 client configuration, if available. */
    private @Autowired(required = false) OAuth2ClientProperties oauth2ClientConfig;

    /** Whether header-based authentication is enabled. */
    private @Value("${georchestra.gateway.headerEnabled:true}") boolean headerEnabled;

    /** Path to the geOrchestra custom stylesheet, if configured. */
    private @Value("${georchestraStylesheet:}") String georchestraStylesheet;

    /** Whether to use the legacy geOrchestra header. */
    private @Value("${useLegacyHeader:false}") boolean useLegacyHeader;

    /** URL of the geOrchestra header component. */
    private @Value("${headerUrl:/header/}") String headerUrl;

    /** Path to the geOrchestra header configuration file. */
    private @Value("${headerConfigFile:}") String headerConfigFile;

    /** Height of the geOrchestra header in pixels. */
    private @Value("${headerHeight:80}") int headerHeight;

    /** URL of the logo displayed in the header. */
    private @Value("${logoUrl:}") String logoUrl;

    /** JavaScript file used to load the geOrchestra header. */
    private @Value("${headerScript:https://cdn.jsdelivr.net/gh/georchestra/header@dist/header.js}") String headerScript;

    /**
     * Initializes authentication settings based on configuration properties.
     * <p>
     * Determines whether LDAP authentication is enabled by checking the configured
     * LDAP servers.
     * </p>
     */
    @PostConstruct
    void initialize() {
        if (georchestraGatewaySecurityConfigProperties != null) {
            ldapEnabled = georchestraGatewaySecurityConfigProperties.getLdap().values().stream()
                    .anyMatch(Server::isEnabled);
        }
    }

    /**
     * Handles logout page rendering.
     * <p>
     * This method sets necessary attributes for rendering the logout page.
     * </p>
     *
     * @param model the model for passing attributes to the view
     * @return the name of the logout view template
     */
    @GetMapping(path = "/logout")
    public String logout(Model model) {
        setHeaderAttributes(model);
        return "logout";
    }

    /**
     * Handles the login page rendering and authentication flow.
     * <p>
     * If only one OAuth2 provider is available and LDAP authentication is disabled,
     * the user is automatically redirected to the provider’s authentication
     * endpoint. Otherwise, the login page is rendered with available authentication
     * options.
     * </p>
     *
     * @param allRequestParams request parameters, including authentication errors
     * @param model            the model for passing attributes to the view
     * @return the login view name or a redirect to an authentication provider
     */
    @GetMapping(path = "/login")
    public String loginPage(@RequestParam Map<String, String> allRequestParams, Model model) {
        Map<String, Pair<String, String>> oauth2LoginLinks = new HashMap<>();

        // Populate OAuth2 login links
        if (oauth2ClientConfig != null) {
            oauth2ClientConfig.getRegistration().forEach((key, value) -> {
                String clientName = Optional.ofNullable(value.getClientName()).orElse(key);
                String providerPath = Path.of("login/img/", key + ".png").toString();
                String logo = new ClassPathResource("static/" + providerPath).exists() ? providerPath
                        : "login/img/default.png";
                oauth2LoginLinks.put("/oauth2/authorization/" + key, Pair.of(clientName, logo));
            });
        }

        // Auto-redirect if only one OAuth2 provider is available and LDAP is disabled
        if (oauth2LoginLinks.size() == 1 && !ldapEnabled) {
            return "redirect:" + oauth2LoginLinks.keySet().stream().findFirst().orElseThrow();
        }

        // Set model attributes for login page rendering
        setHeaderAttributes(model);
        model.addAttribute("ldapEnabled", ldapEnabled);
        model.addAttribute("oauth2LoginLinks", oauth2LoginLinks);

        // Handle authentication error messages
        model.addAttribute("passwordExpired", "expired_password".equals(allRequestParams.get("error")));
        model.addAttribute("invalidCredentials", "invalid_credentials".equals(allRequestParams.get("error")));
        model.addAttribute("duplicateAccount", "duplicate_account".equals(allRequestParams.get("error")));

        return "login";
    }

    /**
     * Sets header-related attributes in the model for rendering views.
     * <p>
     * These attributes control the appearance and behavior of the geOrchestra
     * header displayed on the login and logout pages.
     * </p>
     *
     * @param model the model where attributes will be added
     */
    private void setHeaderAttributes(Model model) {
        model.addAttribute("georchestraStylesheet", georchestraStylesheet);
        model.addAttribute("useLegacyHeader", useLegacyHeader);
        model.addAttribute("headerUrl", headerUrl);
        model.addAttribute("headerHeight", headerHeight);
        model.addAttribute("logoUrl", logoUrl);
        model.addAttribute("headerConfigFile", headerConfigFile);
        model.addAttribute("headerEnabled", headerEnabled);
        model.addAttribute("headerScript", headerScript);
    }
}

# Migrating from security-proxy to geOrchestra Gateway

This guide provides instructions for migrating from the traditional geOrchestra security-proxy to the new geOrchestra Gateway. The Gateway is a modern, reactive replacement for the security-proxy, built on Spring Cloud Gateway and Spring WebFlux.

## Migration Overview

The geOrchestra Gateway provides all the functionality of the security-proxy but with a more modern, flexible architecture:

- **Modern Stack**: Built on Spring Cloud Gateway and Spring WebFlux for high performance and scalability
- **Multiple Authentication Methods**: Supports LDAP, OAuth2/OpenID Connect, and header pre-authentication
- **Reactive Programming Model**: Non-blocking architecture for better resource utilization
- **Simplified Configuration**: YAML-based configuration instead of XML/Properties files
- **Advanced Security Features**: Enhanced role-based access control and header management

## Configuration File Mapping

The following table maps the security-proxy configuration files to their Gateway equivalents:

| security-proxy File | Gateway File | Purpose |
|---------------------|--------------|---------|
| targets-mapping.properties | gateway.yaml | Maps public URLs to backend service targets |
| security-mappings.xml | gateway.yaml | Defines access rules for URLs |
| headers-mapping.properties | gateway.yaml | Configures HTTP headers for proxied requests |
| proxy-permissions.xml | gateway.yaml (security section) | Controls proxy permissions for external URLs |
| security-proxy.properties | security.yaml | General configuration for authentication and security |
| cookie-mappings.json | Not needed (built-in functionality) | Cookie path affinity between services |

## Step-by-Step Migration Guide

### 1. Service Targets and Routing

#### In security-proxy (targets-mapping.properties):
```properties
analytics=http://analytics:8080/analytics/
console=http://console:8080/console/
geonetwork=http://geonetwork:8080/geonetwork/
```

#### In Gateway (gateway.yaml):
```yaml
georchestra:
  gateway:
    services:
      analytics:
        target: http://analytics:8080/analytics/
      console:
        target: http://console:8080/console/
      geonetwork:
        target: http://geonetwork:8080/geonetwork/
```

You need to migrate all your service targets from `targets-mapping.properties` to the `georchestra.gateway.services` section in `gateway.yaml`.

### 2. Access Rules

#### In security-proxy (security-mappings.xml):
```xml
<intercept-url pattern="/analytics/.*" access="ROLE_SUPERUSER,ROLE_ORGADMIN" />
<intercept-url pattern="/console/private/.*" access="ROLE_SUPERUSER,ROLE_ORGADMIN" />
<intercept-url pattern="/console/account/new" access="IS_AUTHENTICATED_ANONYMOUSLY" />
```

#### In Gateway (gateway.yaml):
```yaml
georchestra:
  gateway:
    services:
      analytics:
        access-rules:
        - intercept-url: /analytics/**
          allowed-roles: ROLE_SUPERUSER, ROLE_ORGADMIN
      console:
        access-rules:
        - intercept-url:
          - /console/account/new
          anonymous: true
        - intercept-url:
          - /console/private/**
          allowed-roles: SUPERUSER, ORGADMIN
```

Notes on access rule migration:

- Gateway uses `access-rules` sections per service
- RegEx patterns in security-proxy (`.*`) become glob patterns in Gateway (`**`)
- Roles are defined without the `ROLE_` prefix in Gateway (though both forms work)
- Multiple URL patterns can be combined in a single rule
- The special role `IS_AUTHENTICATED_ANONYMOUSLY` becomes `anonymous: true`

### 3. Header Mappings

#### In security-proxy (headers-mapping.properties):
```properties
sec-email=mail
sec-firstname=givenName
sec-lastname=sn
sec-tel=telephoneNumber
datafeeder.send-json-sec-user=true
datafeeder.send-json-sec-organization=true
```

#### In Gateway (gateway.yaml):
```yaml
georchestra:
  gateway:
    default-headers:
      # Default security headers to append to proxied requests
      proxy: true
      username: true
      roles: true
      org: true
      orgname: true
      email: true
      firstName: true
      lastName: true
      tel: false
      address: false
      jsonUser: false
    services:
      datafeeder:
        headers:
          jsonUser: true
          jsonOrganization: true
```

Notes on header mappings migration:

- Default headers apply to all services unless overridden
- Service-specific headers are defined in each service section
- The format is more standardized with boolean flags for each header
- Special headers like JSON user/organization are represented as `jsonUser` and `jsonOrganization`

### 4. Authentication and Security

#### In security-proxy (security-proxy.properties):
```properties
casTicketValidation=http://${CAS_HOST}:8080/cas
ldapUrl=${ldapScheme}://${ldapHost}:${ldapPort}
ldapBaseDn=${ldapBaseDn:dc=georchestra,dc=org}
ldapUserSearchFilter=(uid={0})
```

#### In Gateway (security.yaml):
```yaml
georchestra:
  gateway:
    security:
      ldap:
        default:
          enabled: true
          url: ${ldapScheme}://${ldapHost}:${ldapPort}
          baseDn: ${ldapBaseDn:dc=georchestra,dc=org}
          users:
            searchFilter: ${ldapUserSearchFilter:(uid={0})}
      oauth2:
        enabled: false
```

The security configuration in Gateway is more comprehensive, supporting multiple authentication methods and multiple LDAP sources.

### 5. Role Mappings

#### In Gateway (roles-mappings.yaml):
```yaml
georchestra:
  gateway:
    roles-mappings:
      '[ROLE_GP.GDI.*]':
        - ROLE_USER
      '[ROLE_GP.GDI.ADMINISTRATOR]':
        - ROLE_SUPERUSER
        - ROLE_ADMINISTRATOR
```

The Gateway adds support for role mappings that let you extend roles based on patterns. This was not available in security-proxy.

### 6. Trusted Proxies

#### In security-proxy (security-proxy.properties):
```properties
trustedProxy=127.0.0.1, 192.168.1.100
```

#### In Gateway (security.yaml):
```yaml
georchestra:
  gateway:
    security:
      header-preauth:
        enabled: true
        trusted-sources:
          - 127.0.0.1
          - 192.168.1.100
```

The Gateway replaces the `trustedProxy` concept with header pre-authentication, which can be configured to trust specific IP addresses.

## Docker Configuration Migration

### Docker Compose

Update your docker-compose.yml:

```yaml
# From
security-proxy:
  image: georchestra/security-proxy:latest
  volumes:
    - ./config/security-proxy:/etc/georchestra/security-proxy

# To
gateway:
  image: georchestra/gateway:latest
  volumes:
    - ./config/gateway:/etc/georchestra/gateway
```

## Advanced Features in Gateway

The Gateway offers several capabilities not available in the security-proxy:

1. **OAuth2/OpenID Connect Authentication**: Configure in security.yaml under `georchestra.gateway.security.oauth2`

2. **Multiple LDAP Sources**: Configure in security.yaml under `georchestra.gateway.security.ldap`

3. **Header Pre-authentication**: For integration with upstream proxies

4. **Structured Logging**: JSON format logging with MDC propagation

## Common Migration Issues

### 1. Authentication Differences

- Gateway does not use CAS for authentication, instead providing direct LDAP, OAuth2, and header pre-authentication
- Session management and cookies are handled differently

### 2. Pattern Matching

- URL patterns in Gateway use Spring's `AntPathMatcher` (glob style) instead of regular expressions
- Test your access rules after migration to ensure they match as expected

### 3. Role Names

- Gateway supports both prefixed (ROLE_ADMIN) and unprefixed (ADMIN) role names
- Review your access rules to ensure roles are correctly defined

### 4. Headers

- Some header names in Gateway differ slightly from security-proxy
- The `sec-` prefix is retained for backward compatibility

## Verifying Your Migration

After migration, verify the following:

1. Authentication works correctly for all methods configured
2. Access rules for each service enforce the correct permissions
3. Headers are correctly passed to backend services
4. Logging and monitoring is properly configured

## Migration Checklist

Use this checklist to ensure you've covered all aspects of migration:

- [ ] **Service Targets**: Migrate all service targets from `targets-mapping.properties` to `gateway.yaml`
- [ ] **Access Rules**: Convert XML access rules to YAML format in `gateway.yaml`
- [ ] **Header Mappings**: Configure header forwarding in `gateway.yaml`
- [ ] **Authentication**: Set up LDAP or OAuth2 authentication in `security.yaml`
- [ ] **Role Mappings**: Configure role mappings in `roles-mappings.yaml` if needed
- [ ] **Header Pre-authentication**: Configure if using an upstream proxy
- [ ] **Docker Configuration**: Update docker-compose files to use the Gateway container
- [ ] **UI Customization**: Migrate any custom CSS or branding
- [ ] **Testing**: Verify all services are accessible and properly secured

## Getting Help

If you encounter issues during migration:

- Check the [Gateway documentation](https://www.georchestra.org/gateway-docs/)
- Ask for help on the [geOrchestra mailing list](https://groups.google.com/forum/#!forum/georchestra)
- File an issue on [GitHub](https://github.com/georchestra/georchestra-gateway/issues)

## Compatibility Mode

The Gateway maintains backward compatibility with the security-proxy in several ways:

- Original `sec-*` headers are still supported alongside the newer formats
- Pattern matching allows for both style of URL patterns
- Role names with and without the `ROLE_` prefix are both supported
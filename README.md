# geOrchestra application gateway service

## Features

- [x] OAuth2 and OpenID Connect authentication and authorization
- [x] LDAP authentication and authorization
- [x] HTTP/2
- [x] Websockets

## Configuration

### LDAP Authentication

LDAP Authentication is enabled and set up through the following
configuration properties in `application.yml`:

```yaml
georchestra.security.ldap:
  enabled: true
  url: ${ldapScheme}://${ldapHost}:${ldapPort}
  baseDn: ${ldapBaseDn:dc=georchestra,dc=org}
  usersRdn: ${ldapUsersRdn:ou=users}
  userSearchFilter: ${ldapUserSearchFilter:(uid={0})}
  rolesRdn: ${ldapRolesRdn:ou=roles}
  rolesSearchFilter: ${ldapRolesSearchFilter:(member={0})}
```

If `georchestra.security.ldap.enabled` is `false`,the log-in page won't show the username/password form inputs.

## Data directory property sources

Routes and other relevant configuration properties are loaded from geOrchestra "data directory"'s
`default.properties` and `gateway/gateway.yaml`.

The location of the data directory is picked up from the `georchestra.datadir` environment property,
and the additional property sources by means of spring-boot's 
`spring.config.import` environment property, like in:
`spring.config.import: ${georchestra.datadir}/default.properties,${georchestra.datadir}/gateway/gateway.yaml`.

## Build

```
make
```

Builds geOrchestra submodule dependencies, the gateway, runs tests,
and builds the docker image.

### Build dependencies only

```
make deps
```

### Build and install without tests

```
make install
```

### Run tests

```
make test
```

## Docker image build

```
make docker
```

Or manually:

```
./mvnw -f gateway [-DimageTag=<tag>] spring-boot:build-image
```

The docker image is created by the `spring-boot-maven-plugin` under the 
`docker` maven profile, which is active by default.

`spring-boot-maven-plugin` builds an OCI compliant image based on Packeto buildpacks.


### Migrating from security-proxy

Security proxy feature set upgrade matrix

| security-proxy | Gateway | Notes |
| --- | --- | --- |
| Per service URI simple routing  | <ul><li>[x] defined in `gateway.yml`</li></ul> | as traditionally defined in `targets-mapping.properties` |
| Global and per-service `sec-*` headers | <ul><li>[ ] defined in `gateway.yml`</li></ul> | as traditionally defined in `headers-mapping.properties` |
| Filter incoming `sec-*` headers | <ul><li>[ ] custom regex based filter</li></ul> | prevents impersonation from outside world |
| `ogc-server-statistics` integration | <ul><li>[ ] </li></ul> |  |
|  | <ul><li>[ ] </li></ul> |  |

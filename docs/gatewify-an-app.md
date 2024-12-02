# Gateway 

![gateway](https://github.com/georchestra/georchestra-gateway/actions/workflows/docker.yml/badge.svg)

The gateway belongs to geOrchestra core, since it is the component which:

* handles user sessions
* routes requests to webapps

The behavior is controlled by the files from the `<datadir_root>/gateway` folder, which can be found [here](https://github.com/georchestra/datadir/tree/master/security-proxy)

## How-to integrate a new application in geOrchestra ?

The goal here is to benefit from the [SSO](https://en.wikipedia.org/wiki/Single_sign-on) feature for the new application without having to use an external authentication process.

### Gateway configuration

1. **Put application behind the gateway**

The new application has to be proxified by the gateway first.

It can be done in the [routes.yaml](https://github.com/georchestra/datadir/blob/master/gateway/routes.yaml) file, which can be found in geOrchestra datadir. Remember: changes in this file requires to restart the gateway.
This file maps public URLs to internal (private) URLs.

To do so, add a new line in the file under `georchestra.gateway.services` in order to map the internal service:

```yaml
georchestra.gateway.services:
  myapp.target: http://localhost:8280/app_path/
```

Then, in order to map the public URL to the internal service, add a new config under `georchestra.cloud.gateway.routes`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: myappname
          uri: ${georchestra.gateway.services.myapp.target}
          predicates:
            - Path=/app_path,/app_path/**
```

- `id` is the name of the route.
- `uri` must point to internal target.
- `predicates` is mostly used with Path predicate, but we can use [other predicates](https://cloud.spring.io/spring-cloud-gateway/multi/multi_gateway-request-predicates-factories.html) like Host, Method, etc.

For instance, both combined, the `app_path` path on gateway's host will be routed to http://localhost:8280/app_path".

2. Set security rules

Now, imagine your application has a public frontend (`/newapp/frontend`) and a private backend (`/newapp/backend`).
You probably would like to restrict backend access to administrators, or people having a specific role.

This can be done very easily with the [gateway.yaml](https://github.com/georchestra/datadir/blob/master/gateway/gateway.yaml) file, also from the geOrchestra datadir.
Here, rules are defined under `georchestra.gateway.services`.

```yaml
georchestra:
  gateway:
    services:
      nawappbackend: 
        target: http://localhost:8080/newapp/frontend/
        access-rules:
        - intercept-url: /newapp/backend/admin*
          allowed-roles: ADMINISTRATOR
        - intercept-url: /newapp/backend/public*
          anonymous: true
      newappfrontend: 
        target: http://localhost:80/newapp/frontend
        access-rules:
        - intercept-url: /newapp/**
          anonymous: true
        headers:
          proxy: true
          username: false
          roles: false
          org: false
          orgname: true
          json-user: true
```

### Application configuration

#### Headers

As you can see above, the `newappfrontend` service has a header section, which is used to override the default headers that will be sent to the frontend application.

In `gateway.yaml` as the start of the file, you can modify the default headers sent to apps:
    
```yaml
    georchestra:
      gateway:
        default-headers:
          proxy: true
          ...
          json-user: true
```


It is also possible to create a specific role in the console app which grants access to the backend, eg with role `NEWAPP_ADMIN`.

#### How it works

With every request, the proxy adds specific HTTP headers, allowing the application to know:

* if the request comes from a registered user, or an anonymous one - this is `sec-username` (not provided if anonymous).
* which roles the user holds - `sec-roles` is a semi-colon separated list of roles (not provided if anonymous).
* which organisation the user belongs to - `sec-orgname` provides the human-readable organisation title while `sec-org` is mapped onto the organisation id (LDAP's `cn`).

Several other user properties are also provided as headers:

* `sec-proxy` tells that request comes from the proxy
* `sec-email` is the user email
* `sec-firstname` is the first name (LDAP `givenName`)
* `sec-lastname` is the second name (LDAP `sn`)
* `sec-tel` is the user phone number (LDAP `telephoneNumber`)

* `sec-json-user` is a json representation of the user object.
* `sec-json-organization` is a json representation of the organization object.

You can find full configuration in [HeaderMappings.java](https://github.com/georchestra/georchestra-gateway/blob/main/gateway/src/main/java/org/georchestra/gateway/model/HeaderMappings.java) file. You just need to rename fields from camelCase (in java file) to kebab-case (in yaml file).

See [here](./custom_filters.adoc#addsecheadersgatewayfilter) for technical details.

The application handles requests appropriately thanks to the headers received.
Some applications will require a direct connection to the LDAP (where users, roles and organisations objects are stored), for instance to list all organisations.

#### Entrypoints

The login entrypoint is `/login` but more generally, one uses the `login` GET parameter in any querystring to force login into a given application.
As a result, the new application may generate links like these: `/newapp/frontend/?login`, for instance if some features in the frontend are only available when authenticated.

Logout entrypoint is `/logout`.
Password recovery form is available from `/console/account/passwordRecovery`.
Account creation form can be found at `/console/account/new`.


#### Cookie Affinity Mapping

Sometimes, cookies sent by one backing service need to be readable by another.
The Gateway will set a cookie path to all backend service cookies to match the service base path (for example,
all cookies sent by the `console` application will have their path set to `/console`.
This makes it impossible for other applications to read them.
A clear case is when the `datahub` application, under the `/datahub` context path, needs access to the
GeoNetwork `XSRF-TOKEN` issued cookie.

**Cookie Affinity Mapping** allows to duplicate cookies set to one path with another path. For the example above,
we need to make it so the GeoNetwork `XSRF-TOKEN` cookie is sent twice to the client, once with `Path=/geonetwork`
and once with `Path=/datahub`.

`gateway.yaml` can be used to configure such cookie affinity. It shall contain an array of objects like the following:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: geonetwork
          uri: ${georchestra.gateway.services.geonetwork.target}
          predicates:
          - Path=/geonetwork/**
          filters:
          - name: CookieAffinity
            args:
              name: XSRF-TOKEN
              from: /geonetwork
              to: /datahub
```

The `name` property indicates the cookie name, the `from` property indicates from which original path the cookie
will be duplicated, and the `to` property which path to duplicate the cookie with.


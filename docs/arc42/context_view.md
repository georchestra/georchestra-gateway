# Context View

## System Context

The geOrchestra Gateway is a central component in the geOrchestra Spatial Data Infrastructure (SDI), acting as the primary entry point for all user interactions with the platform.

### System Context Diagram

The system context diagram below shows the geOrchestra SDI platform (which includes the Gateway) in relation to external systems and users.

![System Context Diagram](../assets/images/structurizr/structurizr-SystemContext.svg)

The diagram shows the following key components and relationships:

1. **Users** interact with the geOrchestra SDI platform using HTTPS
2. **geOrchestra SDI** is the overall system containing the Gateway and other services
3. The **geOrchestra SDI** interacts with external systems:
     - **External Identity Providers** for OAuth2/OpenID Connect authentication
     - **LDAP Server** for user authentication and organization information

> Note: This diagram is generated from the Structurizr DSL definition in `/docs/structurizr/workspace.dsl`. See the [Using Structurizr](using-structurizr.md) guide for more information.

This system context diagram shows how the geOrchestra SDI platform (which includes the Gateway) interacts with users and external systems.

- **Users** interact with the geOrchestra SDI platform via HTTPS requests
- The **geOrchestra SDI** authenticates users with **External Identity Providers** via OAuth2/OpenID Connect
- The **geOrchestra SDI** uses the **LDAP Server** for authentication and organization information

## External Interfaces

The Gateway interacts with several external systems:

### Input Interfaces

1. **End Users (Web Browsers)**
     - Primary consumer of the Gateway's services
     - Sends HTTP requests for geOrchestra resources
     - Receives HTTP responses, typically HTML, CSS, JavaScript, or data

2. **Authentication Providers**
     - LDAP Directory Service: Provides user authentication and user attributes
     - OAuth2/OpenID Connect Providers: External identity providers (e.g., Google, GitHub)
     - Pre-Authentication Proxies: Upstream servers providing pre-authenticated user information

3. **Configuration**
     - Data Directory: External configuration files
     - Environment Variables: Runtime settings

### Output Interfaces

1. **Backend geOrchestra Services**
     - GeoServer: OGC-compliant map and feature server
     - GeoNetwork: Metadata catalog
     - MapStore: Web mapping application
     - Console: User and organization management
     - Analytics: Monitoring and statistics
     - DataFeeder: Data integration tool

2. **Infrastructure Services**
     - LDAP Directory: For user lookups and account management
     - Database (PostgreSQL): Used by other geOrchestra components, not by the Gateway itself

## Technical Context

### Technology Stack

The Gateway is built using:

- Java 21+ programming language
- Spring Boot and Spring Cloud frameworks
- Spring Cloud Gateway for routing
- Spring WebFlux for reactive programming
- Spring Security for authentication and authorization
- Docker for containerization

### Communication Protocols

The Gateway uses the following communication protocols:

- HTTP/HTTPS for web traffic
- LDAP for directory service communication
- AMQP for message broker communication

## Business Context

### User Categories

The Gateway serves several types of users:

1. **End Users**: Members of the public accessing geospatial information
2. **Authenticated Users**: Users with accounts who can access restricted resources
3. **Organization Administrators**: Users who manage organizations and their members
4. **System Administrators**: Users who manage the entire geOrchestra platform

### Stakeholder Concerns

| Stakeholder | Main Concerns |
|-------------|---------------|
| End Users | Security, performance, ease of use |
| System Administrators | Configurability, maintainability, security |
| Developers | API stability, documentation, extensibility |
| Organizations | Data security, access control, user management |

## System Scope and Responsibilities

### Within Scope

The Gateway is responsible for:

1. Authentication of users via multiple methods
2. Authorization based on user roles
3. Routing requests to appropriate backend services
4. Header management for conveying user information
5. User session management
6. Security enforcement for backend services

### Out of Scope

The Gateway is not responsible for:

1. Hosting of actual geospatial data or services
2. User interface beyond the login page
3. User creation and management (handled by Console)
4. Data processing or transformation
5. Metadata management

## Context Mapping

| External System | Interaction Type | Data Exchange |
|-----------------|------------------|---------------|
| End User | HTTP requests/responses | Authentication, service access |
| LDAP Directory | LDAP queries, directory updates | User authentication, user attributes |
| OAuth2 Providers | OAuth2/OIDC protocol | Authentication, user attributes |
| Backend Services | HTTP proxying | Forwarded requests, security headers |
| Database | SQL queries | Used by other components, not by Gateway |
| Message Broker | AMQP messages | Event notifications |

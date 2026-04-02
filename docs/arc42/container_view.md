# Container View

The container view shows the high-level technology choices for the geOrchestra Gateway and how they relate to each other.

## Container Diagram

The container diagram shows the high-level technology choices for the geOrchestra Gateway and how they relate to each other.

![Container Diagram](../assets/images/structurizr/structurizr-Containers.svg)

The diagram illustrates:

1. The **Gateway Spring Boot Application** as the main container
2. Key internal components organized into layers:
     - **API Layer**: Login, logout, and user info endpoints
     - **Authentication Layer**: LDAP, OAuth2, and Header authentication
     - **Routing Layer**: Spring Cloud Gateway routing engine and filter chain
3. External systems that the Gateway interacts with
4. Backend geOrchestra services that the Gateway routes to

> Note: This diagram is generated from the Structurizr DSL definition in `/docs/structurizr/workspace.dsl`. See the [Using Structurizr](using-structurizr.md) guide for more information.

## Container Description

The geOrchestra Gateway consists of several key containers and components:

### Main Container

**Gateway Service (Spring Boot Application)** - The main application container built using Spring Boot, which hosts and coordinates all components.

### Key Component Layers

1. **API Layer**
     - Provides REST APIs for login, logout, and user information retrieval
     - Handles UI for the login page
     - Serves as the entry point for user authentication

2. **Authentication Layer**
     - **LDAP Authentication**: Authenticates users against an LDAP directory
     - **OAuth2 Authentication**: Supports authentication via external OAuth2/OpenID Connect providers
     - **Header Authentication**: Processes pre-authenticated HTTP headers from upstream proxies

3. **Routing Layer**
     - **Routing Engine**: Spring Cloud Gateway core that handles request routing based on predicates
     - **Filter Chain**: Series of filters that process requests/responses, adding headers, applying security, etc.

### External Containers

1. **LDAP Directory**: Stores user accounts, attributes, and group memberships

### Target Backend Services

The Gateway routes requests to various geOrchestra platform services:

- **GeoServer**: OGC-compliant map and feature server
- **GeoNetwork**: Metadata catalog
- **MapStore**: Web mapping application
- **Console**: User and organization management
- **Analytics**: Usage statistics

## Technology Choices

- **Spring Boot**: Application framework
- **Spring WebFlux**: Reactive programming model
- **Spring Cloud Gateway**: API Gateway built on Spring WebFlux
- **Spring Security**: Authentication and authorization framework
- **Docker**: Containerization platform for deployment

## Container Communication

- The Gateway receives HTTPS requests from users and processes them through its filter chain
- Authentication is performed by the appropriate authentication component
- After successful authentication, the routing engine forwards the request to the appropriate backend service
- Communication with backend services is over HTTP/HTTPS
- The LDAP directory is used for authentication and user data retrieval

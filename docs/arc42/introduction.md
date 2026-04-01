# Introduction

## Overview

geOrchestra Gateway is a specialization of Spring Cloud Gateway that provides authentication and proxying services for the geOrchestra Spatial Data Infrastructure. It acts as a central entry point for all geOrchestra applications, handling authentication, authorization, and request routing.

## Technical Context

- **Framework**: Spring Cloud Gateway
- **Authentication**: LDAP, OAuth2/OpenID Connect
- **Security**: Custom Spring Security extensions for role extraction and header forwarding

## Key Requirements

The geOrchestra Gateway serves as a central component in the geOrchestra architecture, with the following requirements:

1. **Single Entry Point**: Act as the single entry point for all geOrchestra applications
2. **Authentication**: Support multiple authentication methods (LDAP, OAuth2/OpenID Connect)
3. **Authorization**: Enforce role-based access control for all applications
4. **Header Forwarding**: Forward user information to backend applications via HTTP headers
5. **Request Routing**: Route requests to the appropriate backend services
6. **Security**: Protect against common web attacks (CSRF, XSS, etc.)

## Stakeholders

| Role | Description | Expectations |
|------|-------------|-------------|
| End Users | Users of geOrchestra applications | Simple, secure authentication; single sign-on |
| System Administrators | Responsible for deploying and maintaining geOrchestra | Easy configuration; good documentation |
| Developers | Contributing to geOrchestra Gateway | Clear code structure; well-defined APIs |
| geOrchestra PSC | Project Steering Committee | Alignment with geOrchestra vision and roadmap |

## System Scope

geOrchestra Gateway is responsible for:

- Authentication of users (LDAP, OAuth2/OpenID Connect)
- Authorization based on user roles
- Request routing to backend services
- Header forwarding to backend applications
- Session management
- Account creation and user management integration

It is **not** responsible for:

- Data storage (relies on LDAP for user information)
- Application-specific business logic
- UI components beyond the login page
- Direct database access
workspace "geOrchestra Gateway" "Software architecture diagrams for the geOrchestra Gateway" {

    model {
        user = person "User" "A user of the geOrchestra platform"
        identityProviders = softwareSystem "External Identity Providers" "OAuth2/OpenID Connect providers"
        ldapServer = softwareSystem "LDAP Server" "Stores user and organization information"

        sdi = softwareSystem "geOrchestra SDI" "Spatial Data Infrastructure platform with various geospatial services" {
            gateway = container "Gateway" "Spring Boot" "Provides authentication, authorization, and routing for geOrchestra services" {
                // Core components
                app = component "Gateway Application" "Spring Boot" "Main application class and bootstrap"
                configProps = component "Gateway Config Properties" "Java" "Configuration model and properties"
                
                // Web API components
                loginController = component "Login Controller" "Spring WebFlux" "Handles user login and logout"
                whoamiController = component "Whoami Controller" "Spring WebFlux" "Provides user information API"
                
                // Security components
                securityConfig = component "Security Configuration" "Spring Security" "Configures security for the Gateway"
                userMapper = component "User Mapper" "Java" "Maps authentication details to geOrchestra user model"
                ldapAuth = component "LDAP Authentication" "Spring LDAP" "Authenticates users against LDAP directory"
                oauth2Auth = component "OAuth2 Authentication" "Spring OAuth2" "Authenticates users using OAuth2/OpenID Connect"
                headerAuth = component "Header Pre-authentication" "Java" "Authenticates users from HTTP headers"
                
                // Filter components
                resolveUserFilter = component "Resolve User Filter" "Spring WebFilter" "Resolves user details for the request"
                resolveTargetFilter = component "Resolve Target Filter" "Spring WebFilter" "Resolves target service for the request"
                headerFilters = component "Header Filters" "Gateway Filter" "Manage HTTP headers in requests/responses"
                errorFilter = component "Error Filter" "Gateway Filter" "Handles errors in request processing"
            }
            
            geoserver = container "GeoServer" "Java" "OGC-compliant server for publishing geospatial data"
            console = container "Console" "Java" "Administration interface for managing users and organizations"
            mapstore = container "MapStore" "JavaScript" "Web mapping application"
            other = container "Other Services" "Various" "Other geOrchestra services"
        }
        
        // External relationships
        user -> gateway "Uses"
        gateway -> identityProviders "Authenticates with"
        gateway -> ldapServer "Authenticates and authorizes using"
        gateway -> console "Publishes events to"

        // Internal relationships
        gateway -> geoserver "Routes requests to"
        gateway -> console "Routes requests to"
        gateway -> mapstore "Routes requests to"
        gateway -> other "Routes requests to"
        
        // Component relationships
        app -> configProps "Uses"
        securityConfig -> ldapAuth "Configures"
        securityConfig -> oauth2Auth "Configures"
        securityConfig -> headerAuth "Configures"
        userMapper -> resolveUserFilter "Used by"
        resolveUserFilter -> resolveTargetFilter "Precedes"
        resolveTargetFilter -> headerFilters "Precedes"
    }
    
    views {
        systemContext sdi "SystemContext" "System Context diagram for geOrchestra Gateway" {
            include *
            autoLayout
        }
        
        container sdi "Containers" "Container diagram for geOrchestra system" {
            include *
            autoLayout
        }
        
        component gateway "Components" "Component diagram for the geOrchestra Gateway" {
            include *
            autoLayout
        }

        styles {
            element "Person" {
                shape Person
                background #08427B
                color #ffffff
            }
            element "Software System" {
                background #1168BD
                color #ffffff
            }
            element "Container" {
                background #438DD5
                color #ffffff
            }
            element "Component" {
                background #85BBF0
                color #000000
            }
        }
    }
}
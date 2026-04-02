workspace "geOrchestra Gateway Dynamic Views" {

    model {
        user = person "User" "A user of the geOrchestra platform"
        
        sdi = softwareSystem "geOrchestra SDI" {
            gateway = container "Gateway" "Spring Boot" {
                securityFilter = component "Security Filter Chain" "Spring Security WebFilter"
                resolveUserFilter = component "Resolve User Filter" "WebFilter"
                resolveTargetFilter = component "Resolve Target Filter" "WebFilter"
                headerFilters = component "Header Filters" "Gateway Filter"
                routingHandler = component "Routing Handler" "Spring Cloud Gateway"
            }
            
            targetService = container "Target Service" "Any geOrchestra service"
            console = container "Console" "Java" "Administration interface" {
            }
        }
        
        # Define regular relationships
        user -> securityFilter "Sends HTTP request"
        securityFilter -> user "Redirects to OAuth provider" "HTTP 302"
        targetService -> user "Returns response" "HTTP"
        
        # Define all the exact relationships needed in dynamic views
        securityFilter -> securityFilter "Authenticates user"
        securityFilter -> securityFilter "Exchanges code for tokens"
        securityFilter -> securityFilter "Creates authentication"
        
        securityFilter -> resolveUserFilter "Forwards authenticated request" "Spring WebFilter chain"
        
        resolveUserFilter -> resolveUserFilter "Resolves user details"
        resolveUserFilter -> resolveTargetFilter "Forwards request with user details" "Spring WebFilter chain"
        
        resolveTargetFilter -> resolveTargetFilter "Resolves target service"
        resolveTargetFilter -> headerFilters "Forwards request with target" "Gateway FilterChain"
        
        headerFilters -> headerFilters "Processes headers"
        headerFilters -> routingHandler "Forwards request with modified headers" "Gateway FilterChain"
        
        routingHandler -> targetService "Routes request" "HTTP"
        
        gateway -> console "Publishes events to"
        securityFilter -> eventSender "Sends user events"
    }
    
    views {
        dynamic gateway "AuthenticationFlow" "Authentication and request processing flow" {
            user -> securityFilter "Sends HTTP request"
            securityFilter -> securityFilter "Authenticates user"
            securityFilter -> resolveUserFilter "Forwards authenticated request" "Spring WebFilter chain"
            resolveUserFilter -> resolveUserFilter "Resolves user details"
            resolveUserFilter -> resolveTargetFilter "Forwards request with user details" "Spring WebFilter chain"
            resolveTargetFilter -> resolveTargetFilter "Resolves target service"
            resolveTargetFilter -> headerFilters "Forwards request with target" "Gateway FilterChain"
            headerFilters -> headerFilters "Processes headers"
            headerFilters -> routingHandler "Forwards request with modified headers" "Gateway FilterChain"
            routingHandler -> targetService "Routes request" "HTTP"
            targetService -> user "Returns response" "HTTP"
            
            autoLayout
        }
        
        dynamic gateway "OAuthFlow" "OAuth2 authentication flow" {
            user -> securityFilter "Sends HTTP request"
            securityFilter -> user "Redirects to OAuth provider" "HTTP 302"
            user -> securityFilter "Sends HTTP request"
            securityFilter -> securityFilter "Exchanges code for tokens"
            securityFilter -> securityFilter "Creates authentication"
            securityFilter -> resolveUserFilter "Forwards authenticated request" "Spring WebFilter chain"
            
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
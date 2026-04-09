# Deployment View

The deployment view describes how the geOrchestra Gateway is deployed in production environments and the infrastructure required to support it.

## Overview

The geOrchestra Gateway is designed as a Spring Boot microservice that can be run as a simple executable, a system service, or be deployed as a containerized application (typically using Docker). It is part of the broader geOrchestra Spatial Data Infrastructure (SDI) ecosystem and acts as the central entry point for all user requests to geOrchestra services.

## Deployment Diagram

```mermaid
flowchart TB
    classDef container fill:#438DD5,stroke:#2E6295,color:#fff
    classDef infra fill:#85BBF0,stroke:#5D82A8,color:#000
    classDef external fill:#999999,stroke:#6B6B6B,color:#fff
    classDef volume fill:#85BBF0,stroke:#5D82A8,color:#000,shape:cylinder

    subgraph dm["Deployment Environment"]
        direction TB

        lb[Load Balancer]:::infra

        subgraph dc["Docker Environment"]
            gateway[Gateway]:::container

            subgraph ms["geOrchestra Microservices"]
                geoserver[GeoServer]:::container
                geonetwork[GeoNetwork]:::container
                mapstore[MapStore]:::container
                console[Console]:::container
                analytics[Analytics]:::container
            end

            subgraph vol["Volumes"]
                datadir[Data Directory]:::volume
                geoserver_data[GeoServer Data]:::volume
                db_data[Database Data]:::volume  %% Used by other services, not Gateway
                ldap_data[LDAP Data]:::volume
            end
        end

        lb --> gateway

        gateway --> ms
        gateway --> infra
        gateway --> datadir

        geoserver --> geoserver_data
        postgres --> db_data
        ldap --> ldap_data
    end

    user([Users]):::external --> lb
    oidc([Identity Providers]):::external --> gateway
```

## Deployment Units

The deployment consists of several key units:

### Gateway Container

The geOrchestra Gateway is deployed as a Docker container with the following characteristics:

- **Image**: `georchestra/gateway:latest` (or specific version)
- **Resources**:
    - Minimum: 1 CPU, 512MB RAM
    - Recommended: 2 CPU, 1GB RAM
- **Ports**: Exposes port `8080` internally
- **Dependencies**:
    - LDAP for authentication

### Configuration Volume

The Gateway's configuration is externalized in a data directory volume:

- **Path**: `/etc/georchestra` (inside the container)
- **Contents**:
    - `default.properties` - Properties common to all geOrchestra applications
    - `gateway/gateway.yaml` - Gateway configuration
    - `gateway/security.yaml` - Security configuration
    - `gateway/routes.yaml` - Route definitions
    - `gateway/roles-mappings.yaml` - Role mappings

### Infrastructure Services

The Gateway depends on several infrastructure services:

1. **LDAP Directory**:
     - Stores user accounts, groups, and organizations
     - Typically deployed as `georchestra/ldap` container
     - Requires persistent volume for data

2. **PostgreSQL Database**: 
     - Used by other geOrchestra components (Console, GeoServer, etc.), but not by the Gateway itself
     - Typically deployed as `postgres:14` container
     - Requires persistent volume for data

### Load Balancer

In production environments, a load balancer sits in front of the Gateway:

- Handles TLS termination
- Distributes traffic in multi-instance deployments
- Manages session affinity if needed
- Provides health checks

## Hardware Requirements

The hardware requirements depend on the scale of the deployment:

### Small Deployment (< 100 concurrent users)

- **Gateway**: 2 CPU cores, 1-2GB RAM
- **Infrastructure Services**: 4 CPU cores, 8GB RAM total
- **Storage**: 10GB for configuration and data

### Medium Deployment (100-500 concurrent users)

- **Gateway**: 4 CPU cores, 4GB RAM
- **Infrastructure Services**: 8 CPU cores, 16GB RAM total
- **Storage**: 50GB for configuration and data

### Large Deployment (> 500 concurrent users)

- **Gateway**: 8+ CPU cores, 8+ GB RAM, multiple instances
- **Infrastructure Services**: 16+ CPU cores, 32+ GB RAM total
- **Storage**: 100+ GB for configuration and data, potentially distributed

## Deployment Options

### Standalone Java Application

As a Spring Boot application, the Gateway can be run directly as a Java application:

```bash
# Run the JAR file directly
java -jar gateway.jar --spring.config.location=file:/path/to/config/

# Or using the Spring Boot Maven plugin
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -pl gateway
```

### System Service

For traditional server environments, the Gateway can be installed as a system service:

#### Systemd Service Example

```ini
[Unit]
Description=geOrchestra Gateway
After=network.target

[Service]
User=georchestra
ExecStart=/usr/bin/java -jar /opt/georchestra/gateway.jar
Environment="SPRING_CONFIG_LOCATION=/etc/georchestra/"
Environment="GEORCHESTRA_DATADIR=/etc/georchestra"
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

### Docker Compose

For development and small production environments, Docker Compose provides a straightforward deployment option:

```yaml
version: '3'
services:
  gateway:
    image: georchestra/gateway:latest
    depends_on:
      - ldap
    volumes:
      - georchestra_datadir:/etc/georchestra
    environment:
      - JAVA_TOOL_OPTIONS=-Dgeorchestra.datadir=/etc/georchestra
    ports:
      - "8080:8080"
```

### Kubernetes

For larger, more scalable deployments, Kubernetes is recommended:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: georchestra-gateway
spec:
  replicas: 2  # Multiple instances for high availability
  selector:
    matchLabels:
      app: georchestra-gateway
  template:
    metadata:
      labels:
        app: georchestra-gateway
    spec:
      containers:
      - name: gateway
        image: georchestra/gateway:latest
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2"
        ports:
        - containerPort: 8080
        env:
        - name: JAVA_TOOL_OPTIONS
          value: "-Dgeorchestra.datadir=/etc/georchestra"
        volumeMounts:
        - name: config-volume
          mountPath: /etc/georchestra
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 30
      volumes:
      - name: config-volume
        configMap:
          name: georchestra-config
```

## Runtime Dependencies

### Runtime Environment

- Java 21 or higher runtime environment
- One of the following:
    - Standard operating system (Linux, Windows, macOS) for standalone deployment
    - Docker 19.03 or higher (for containerized deployment)
    - Kubernetes 1.19 or higher (for orchestrated deployment)

### External Systems

- **LDAP Directory Service**: Compatible with OpenLDAP, Active Directory
- **PostgreSQL Database**: Version 12 or higher (used by other geOrchestra components, not by Gateway itself)

### Network Requirements

- Internal network for service-to-service communication
- External network access for user requests
- Internal network connectivity to LDAP, PostgreSQL (for other components)
- Optional external network access for OAuth2/OpenID Connect providers

## Scaling Strategies

### Horizontal Scaling

The Gateway can be horizontally scaled by deploying multiple instances behind a load balancer:

- **Session Management**: Enable sticky sessions on the load balancer (the Gateway does not implement session sharing)
- **Configuration**: Ensure all instances use the same configuration
- **Load Balancing**: Sticky sessions with cookie-based affinity is recommended

### Vertical Scaling

For simpler deployments, vertical scaling can be effective:

- Increase CPU and memory allocations
- Tune JVM parameters for better performance
- Optimize connection pools and thread counts

## Monitoring and Operations

### Health Checks

The Gateway exposes Actuator endpoints for monitoring:

- `/actuator/health` - Overall health status
- `/actuator/health/liveness` - Liveness check for Kubernetes
- `/actuator/health/readiness` - Readiness check for Kubernetes

### Metrics

Metrics are available through the Actuator metrics endpoint:

- `/actuator/metrics` - List of available metrics
- `/actuator/metrics/{metric.name}` - Specific metric details

### Logging

Logs are written to standard output in Docker environments, with several options:

- **Standard Logging**: Plain text logs
- **JSON Logging**: Structured logs for log aggregation systems (recommended for production)
- **Access Logging**: HTTP request logging with configurable patterns
- **MDC Properties**: Additional context in logs for tracing

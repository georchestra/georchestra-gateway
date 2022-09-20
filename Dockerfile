FROM mtr.devops.telekom.de/geo-hub/maven-dt-ftth:3-jdk-11 AS build

ARG REPO_USER
ARG REPO_PW
ARG REPO_URL

WORKDIR /build
COPY georchestra georchestra
COPY .mvn .mvn

RUN mvn install -DskipTests -f georchestra/pom.xml --non-recursive && \
    mvn install -DskipTests -f georchestra/commons && \
    mvn install -DskipTests -f georchestra/testcontainers && \
    mvn install -DskipTests -f georchestra/ldap-account-management

COPY gateway gateway
COPY pom.xml pom.xml

RUN mvn package -P-georchestra

FROM mtr.devops.telekom.de/geo-hub/zulu-openjdk:11

WORKDIR /app
COPY --from=build /build/gateway/target/*.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]

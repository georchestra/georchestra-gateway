.DEFAULT_GOAL := all

all: deps install test docker

deps:
	./mvnw clean install -f georchestra/commons -ntp -DskipTests && \
	./mvnw clean install -f georchestra/testcontainers -ntp -DskipTests && \
	./mvnw clean install -f georchestra/ldap-account-management -ntp -DskipTests

install:
	./mvnw clean install -pl :georchestra-gateway -ntp -DskipTests

test:
	./mvnw verify -pl :georchestra-gateway -ntp

docker:
	@TAG=`./mvnw -f gateway/ help:evaluate -q -DforceStdout -Dexpression=imageTag` && \
	./mvnw package -f gateway/ -Pdocker -ntp -DskipTests && \
	echo tagging georchestra/gateway:$${TAG} as georchestra/gateway:latest && \
	docker tag georchestra/gateway:$${TAG} georchestra/gateway:latest && \
	docker images|grep "georchestra/gateway"|grep latest

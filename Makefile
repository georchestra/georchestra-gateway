all: install test docker

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

deb: install
	./mvnw package deb:package -f gateway/ -PdebianPackage
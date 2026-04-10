all: install test docker

install:
	./mvnw clean install -ntp -DskipTests

test:
	./mvnw verify -ntp

docker:
	./mvnw install -ntp -DskipTests -pl modules/org-resolvers,modules/sirene,modules/logging -am
	@TAG=`./mvnw -f gateway/ help:evaluate -q -DforceStdout -Dexpression=imageTag` && \
	./mvnw package -f gateway/ -Pdocker -ntp -DskipTests && \
	echo tagging georchestra/gateway:$${TAG} as georchestra/gateway:latest && \
	docker tag georchestra/gateway:$${TAG} georchestra/gateway:latest && \
	docker images|grep "georchestra/gateway"|grep latest

docker-debug:
	./mvnw install -ntp -DskipTests -pl modules/org-resolvers,modules/sirene,modules/logging -am
	@TAG=`./mvnw -f gateway/ help:evaluate -q -DforceStdout -Dexpression=imageTag` && \
	./mvnw package -f gateway/ -Pdocker,docker-debug -ntp -DskipTests && \
	docker tag georchestra/gateway:$${TAG}-debug georchestra/gateway:latest-debug

deb: install
	./mvnw package deb:package -f gateway/ -PdebianPackage

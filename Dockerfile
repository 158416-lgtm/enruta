# Imagen generica multi-modulo. Se construye una por servicio con --build-arg MODULE=<nombre>.
#
#   docker build -f Dockerfile --build-arg MODULE=shipment-service -t enruta/shipment-service .
#
# El contexto es la raiz del repo para que Maven resuelva el reactor completo.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY pom.xml ./
COPY contracts/pom.xml contracts/
COPY common/pom.xml common/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/shipment-service/pom.xml services/shipment-service/
COPY services/dispatch-service/pom.xml services/dispatch-service/
COPY services/tracking-service/pom.xml services/tracking-service/
COPY services/routing-eta-service/pom.xml services/routing-eta-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY tools/driver-simulator/pom.xml tools/driver-simulator/

RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:go-offline

COPY contracts/src contracts/src
COPY common/src common/src
COPY services services
COPY tools tools

ARG MODULE
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
RUN addgroup -S enruta && adduser -S enruta -G enruta
COPY --from=build /build/services/${MODULE}/target/*.jar /app/app.jar
USER enruta

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

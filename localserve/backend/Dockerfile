# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY backend/pom.xml backend/pom.xml
COPY backend/shared-kernel/pom.xml backend/shared-kernel/pom.xml
COPY backend/identity-access/pom.xml backend/identity-access/pom.xml
COPY backend/people/pom.xml backend/people/pom.xml
COPY backend/catalog-search/pom.xml backend/catalog-search/pom.xml
COPY backend/location/pom.xml backend/location/pom.xml
COPY backend/booking-dispatch/pom.xml backend/booking-dispatch/pom.xml
COPY backend/finance/pom.xml backend/finance/pom.xml
COPY backend/communication/pom.xml backend/communication/pom.xml
COPY backend/reputation-growth/pom.xml backend/reputation-growth/pom.xml
COPY backend/case-management/pom.xml backend/case-management/pom.xml
COPY backend/administration-analytics/pom.xml backend/administration-analytics/pom.xml
COPY backend/file-management/pom.xml backend/file-management/pom.xml
COPY backend/application/pom.xml backend/application/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl backend/application -am dependency:go-offline
COPY backend backend
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl backend/application -am verify -DskipTests=false

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install --yes --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 localserve \
    && useradd --uid 10001 --gid localserve --no-create-home --shell /usr/sbin/nologin localserve
WORKDIR /app
COPY --from=build --chown=localserve:localserve /workspace/backend/application/target/application-*.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD curl --fail --silent --show-error http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:+ExitOnOutOfMemoryError","-Djava.security.egd=file:/dev/urandom","-jar","/app/app.jar"]

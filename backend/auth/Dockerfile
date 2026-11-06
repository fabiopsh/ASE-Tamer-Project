# syntax=docker/dockerfile:1
# auth - build context: backend/ (see docs/plan/02-infrastructure.md, INFRA-2)

# ---- build stage: compiles inside Docker, the host needs only Docker ----
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
# The aggregator needs every module pom to resolve the reactor
COPY pom.xml .
COPY auth/pom.xml auth/
COPY players/pom.xml players/
COPY monsters/pom.xml monsters/
COPY teams/pom.xml teams/
COPY encounters/pom.xml encounters/
COPY history/pom.xml history/
COPY auth/src auth/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl auth -am package -DskipTests

# ---- runtime stage: non-root (requirements §6) ----
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24
COPY --from=build --chown=185 /src/auth/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /src/auth/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /src/auth/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /src/auth/target/quarkus-app/quarkus/ /deployments/quarkus/
USER 185
EXPOSE 8443
ENV JAVA_APP_JAR=/deployments/quarkus-run.jar \
    JAVA_OPTS_APPEND="-Djava.util.logging.manager=org.jboss.logmanager.LogManager" \
    QUARKUS_PROFILE=prod \
    QUARKUS_CONFIG_LOCATIONS=/run/secrets/secrets.properties
ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]

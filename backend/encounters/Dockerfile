# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /src/target/quarkus-app/ /app/
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
  CMD wget -qO- http://localhost:8080/q/health/ready || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]

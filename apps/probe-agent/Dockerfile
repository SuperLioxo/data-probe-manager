# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY apps/probe-agent/pom.xml ./apps/probe-agent/pom.xml

RUN mvn -B dependency:go-offline -pl apps/probe-agent -am

COPY apps/probe-agent/ ./apps/probe-agent/
RUN mvn -B -pl apps/probe-agent -am package -DskipTests

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S probe && adduser -S probe -G probe

WORKDIR /app
COPY --from=build /build/apps/probe-agent/target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R probe:probe /app

USER probe
EXPOSE 58081

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]

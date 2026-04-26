# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy parent POM first for dependency resolution
COPY pom.xml ./
COPY apps/probe-admin/pom.xml ./apps/probe-admin/pom.xml

# Download dependencies (cached layer)
RUN mvn -B dependency:go-offline -pl apps/probe-admin -am

# Copy source and build
COPY apps/probe-admin/ ./apps/probe-admin/
RUN mvn -B -pl apps/probe-admin -am package -DskipTests

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S probe && adduser -S probe -G probe

WORKDIR /app
COPY --from=build /build/apps/probe-admin/target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R probe:probe /app

USER probe
EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]

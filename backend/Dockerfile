# ==============================================================================
# EuroJob Copilot Backend - Multi-stage Production Dockerfile
# ==============================================================================

# Stage 1: Build with Maven & JDK 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache Maven dependencies before copying source
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package application JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Runtime Container with JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for container health checks, prepare writable storage/log dirs, and run as non-root user
RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /var/lib/eurojob-copilot/resumes /var/log/eurojob-copilot \
    && chown -R spring:spring /app /var/lib/eurojob-copilot /var/log/eurojob-copilot

USER spring:spring

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

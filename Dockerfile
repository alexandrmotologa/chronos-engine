# Multi-stage Dockerfile for Chronos Engine

# Build Stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S chronos && adduser -S chronos -G chronos
USER chronos:chronos

COPY --from=builder /workspace/target/chronos-engine-0.1.0-SNAPSHOT.jar /app/chronos-engine.jar

EXPOSE 8080

# Configure Generational ZGC for low-latency GC pauses
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/chronos-engine.jar"]

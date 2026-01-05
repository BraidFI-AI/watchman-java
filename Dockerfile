# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install curl for maven wrapper
RUN apk add --no-cache curl

# Copy maven wrapper and pom
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install Python and cron for agent system
RUN apk add --no-cache python3 py3-pip dcron

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Copy agent scripts and configuration
COPY scripts/*.py scripts/requirements.txt scripts/crontab /app/scripts/
RUN pip3 install --break-system-packages --no-cache-dir -r /app/scripts/requirements.txt && \
    chmod +x /app/scripts/*.py

# Create data directories for sanctions list cache and agent reports
RUN mkdir -p /app/data /data/reports /data/logs && \
    chown -R appuser:appgroup /app /data

# Setup cron - don't install crontab yet, will do at runtime
RUN chmod 644 /app/scripts/crontab

USER appuser

# Expose port (Fly.io uses 8080 internally)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# JVM options for container environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Create startup script that initializes cron and starts Java
RUN echo '#!/bin/sh' > /app/start.sh && \
    echo 'crontab /app/scripts/crontab' >> /app/start.sh && \
    echo 'crond -b' >> /app/start.sh && \
    echo 'exec java $JAVA_OPTS -jar /app/app.jar' >> /app/start.sh && \
    chmod +x /app/start.sh

ENTRYPOINT ["/app/start.sh"]

# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install curl for maven wrapper and python for API generation
RUN apk add --no-cache curl python3

# Copy maven wrapper and pom
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests -B

# Generate API reference for repair agent
COPY scripts/generate_api_reference.py scripts/
RUN python3 scripts/generate_api_reference.py

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

# Copy source code for code analysis by repair agent
COPY --from=build /app/src/ /app/src/

# Copy API reference for repair agent
COPY --from=build /app/target/API-REFERENCE.md /app/API-REFERENCE.md

# Copy agent scripts and configuration
COPY scripts/*.py scripts/requirements.txt scripts/crontab /app/scripts/
COPY scripts/nemesis/ /app/scripts/nemesis/
RUN pip3 install --break-system-packages --no-cache-dir -r /app/scripts/requirements.txt && \
    chmod +x /app/scripts/*.py /app/scripts/nemesis/*.py

# Create data directories for sanctions list cache and agent reports
RUN mkdir -p /app/data /data/reports /data/logs && \
    chown -R appuser:appgroup /app /data

# Setup cron
RUN chmod 644 /app/scripts/crontab

# Expose port (Fly.io uses 8080 internally)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# JVM options for container environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Create startup script that starts cron as root, then drops to appuser for Java
RUN echo '#!/bin/sh' > /app/start.sh && \
    echo '# Copy crontab to /etc/crontabs/ for appuser' >> /app/start.sh && \
    echo 'mkdir -p /etc/crontabs' >> /app/start.sh && \
    echo 'cp /app/scripts/crontab /etc/crontabs/appuser' >> /app/start.sh && \
    echo '# Fix permissions on /data directories' >> /app/start.sh && \
    echo 'mkdir -p /data/reports /data/logs /data/state' >> /app/start.sh && \
    echo 'chown -R appuser:appgroup /data' >> /app/start.sh && \
    echo 'crond -b -l 2' >> /app/start.sh && \
    echo '# Switch to appuser and start Java' >> /app/start.sh && \
    echo 'exec su-exec appuser java $JAVA_OPTS -jar /app/app.jar' >> /app/start.sh && \
    chmod +x /app/start.sh && \
    apk add --no-cache su-exec

ENTRYPOINT ["/app/start.sh"]

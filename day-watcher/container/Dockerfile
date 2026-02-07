# Day Watcher Container
# Java Watchman (Spring Boot on :8084) + Python worker (batch screening)

FROM eclipse-temurin:21-jre

# Install Python 3 and pip
RUN apt-get update && \
    apt-get install -y python3 python3-pip curl && \
    rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy Java Watchman JAR from day-watcher/container/jar/
COPY day-watcher/container/jar/watchman.jar /app/watchman.jar

# Copy Python worker files
COPY day-watcher/container/batch_worker.py /app/
COPY day-watcher/container/enrichment.py /app/
COPY day-watcher/container/start.sh /app/
COPY day-watcher/container/requirements.txt /app/

# Install Python dependencies
RUN pip3 install --break-system-packages --no-cache-dir -r requirements.txt

# Make start script executable
RUN chmod +x /app/start.sh

# Expose Java Watchman port (internal only, not published)
EXPOSE 8084

# Environment variables (overridden by ECS task)
ENV RUN_ID=""
ENV INPUT_BUCKET=""
ENV INPUT_KEY=""
ENV RESULTS_BUCKET=""
ENV DYNAMODB_TABLE=""
ENV CHUNK_SIZE="1000"
ENV JAVA_WATCHMAN_PORT="8084"

# Start script launches Java Watchman, then Python worker
CMD ["/app/start.sh"]

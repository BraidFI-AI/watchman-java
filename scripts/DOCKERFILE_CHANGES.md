# Dockerfile Changes for Agent Support

Add these sections to your existing Dockerfile to enable Nemesis/Analyzer agents:

```dockerfile
# At the top, after FROM statement
FROM eclipse-temurin:21-jre-alpine

# Add Python and cron support for agents
RUN apk add --no-cache \
    python3 \
    py3-pip \
    dcron

# ... your existing Java app setup ...

# Near the end, before CMD
# Install agent scripts and dependencies
COPY scripts/ /app/scripts/
RUN pip3 install --no-cache-dir -r /app/scripts/requirements.txt

# Setup cron for scheduled agent execution
COPY scripts/crontab /etc/crontabs/root

# Create directories for reports and logs
RUN mkdir -p /data/reports /data/logs && \
    chmod 755 /data/reports /data/logs

# Modified CMD to start both cron and Java app
CMD ["sh", "-c", "crond && java ${JAVA_OPTS} -jar /app/app.jar"]
```

## What This Does

1. **Adds Python 3** - Required to run the agent scripts
2. **Adds cron** - Schedules weekly Nemesis/Analyzer runs
3. **Copies scripts** - Makes agents available in container
4. **Installs dependencies** - anthropic/openai/requests packages
5. **Sets up scheduling** - Runs agents every Monday
6. **Creates directories** - Persistent storage for reports
7. **Modified startup** - Runs both cron and your Java app

## Minimal Impact

- Image size increase: ~50MB (Python + dependencies)
- Memory usage: ~20MB extra for cron + Python
- CPU usage: Only when agents run (1 hour/week)
- No impact on Java app performance

## Alternative: No Cron (Manual Execution Only)

If you don't want scheduled execution, skip the cron parts:

```dockerfile
# Just add Python (no cron)
RUN apk add --no-cache python3 py3-pip

COPY scripts/ /app/scripts/
RUN pip3 install --no-cache-dir -r /app/scripts/requirements.txt

# Keep original CMD
CMD ["java", "-jar", "/app/app.jar"]
```

Then run manually via SSH:
```bash
fly ssh console -a watchman-java
python3 /app/scripts/run-nemesis.py
```

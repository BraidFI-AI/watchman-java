# Deployment Guide

This guide covers deploying Watchman Java to Fly.io and other environments.

---

## Table of Contents

1. [Fly.io Deployment](#flyio-deployment)
2. [Docker Deployment](#docker-deployment)
3. [Local Development](#local-development)
4. [Configuration](#configuration)
5. [Monitoring & Operations](#monitoring--operations)
6. [Troubleshooting](#troubleshooting)

---

## Fly.io Deployment

### Prerequisites

- [Fly.io CLI](https://fly.io/docs/hands-on/install-flyctl/) installed
- Fly.io account (`flyctl auth signup` or `flyctl auth login`)
- Java 21 and Maven (for local builds)

### Initial Deployment

```bash
# Create the app (first time only)
flyctl apps create watchman-java --org personal

# Deploy
flyctl deploy --remote-only
```

### Subsequent Deployments

```bash
# Deploy latest code
flyctl deploy --remote-only

# Deploy with specific image tag
flyctl deploy --image watchman-java:v1.0.0
```

### Deployment Configuration

The deployment is configured via `fly.toml`:

| Setting | Value | Description |
|---------|-------|-------------|
| `app` | `watchman-java` | Application name |
| `primary_region` | `ord` | Chicago datacenter |
| `internal_port` | `8080` | Container port |
| `memory` | `1gb` | RAM per machine |
| `cpus` | `1` | Shared CPU |

### Environment Variables

Set via `fly.toml` or Fly.io dashboard:

```toml
[env]
  JAVA_OPTS = '-Xmx512m -Xms256m'
  SERVER_PORT = '8080'
  SPRING_PROFILES_ACTIVE = 'production'
```

### Secrets (if needed)

```bash
# Set a secret
flyctl secrets set API_KEY=your-secret-key

# List secrets
flyctl secrets list
```

---

## Docker Deployment

### Build Image Locally

```bash
# Build the Docker image
docker build -t watchman-java:latest .

# Run locally
docker run -p 8080:8080 watchman-java:latest
```

### Multi-Architecture Build

```bash
# Build for multiple platforms
docker buildx build --platform linux/amd64,linux/arm64 \
  -t watchman-java:latest .
```

### Docker Compose

```yaml
version: '3.8'
services:
  watchman:
    build: .
    ports:
      - "8084:8080"
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m
      - SPRING_PROFILES_ACTIVE=production
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

---

## Local Development

### Run with Maven

```bash
# Run the application
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=production

# Run tests
./mvnw test
```

### Default Ports

| Environment | Port | URL |
|-------------|------|-----|
| Local (dev) | 8084 | http://localhost:8084 |
| Docker | 8080 | http://localhost:8080 |
| Fly.io | 443 | https://watchman-java.fly.dev |

---

## Configuration

### Application Profiles

| Profile | File | Use Case |
|---------|------|----------|
| `default` | `application.properties` | Local development |
| `production` | `application-production.properties` | Fly.io / Production |

### Key Configuration Properties

```properties
# Server
server.port=8080

# Data Sources
watchman.sources.ofac.enabled=true
watchman.sources.us-csl.enabled=true
watchman.sources.eu-csl.enabled=true
watchman.sources.uk-csl.enabled=true

# Search Defaults
watchman.search.default-min-match=0.85
watchman.search.default-limit=10

# Batch Screening
watchman.batch.max-size=1000
watchman.batch.parallel-threads=4

# Download
watchman.download.on-startup=true
watchman.download.refresh-interval=86400000
```

---

## Monitoring & Operations

### Health Check

```bash
# Check health
curl https://watchman-java.fly.dev/health

# Expected response
{
  "status": "healthy",
  "entityCount": 28500
}
```

### Fly.io Commands

```bash
# Check app status
flyctl status

# View logs
flyctl logs

# View logs (follow)
flyctl logs -f

# SSH into machine
flyctl ssh console

# Check machine metrics
flyctl machine status

# Scale machines
flyctl scale count 2

# Scale memory
flyctl scale memory 2048
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/v2/search` | GET | Search entities |
| `/v2/listinfo` | GET | List statistics |
| `/v2/search/batch` | POST | Batch screening |
| `/v2/search/batch/config` | GET | Batch config |
| `/v1/download/refresh` | POST | Trigger data refresh |
| `/v1/download/status` | GET | Download status |

### Quick API Test

```bash
# Search for an entity
curl "https://watchman-java.fly.dev/v2/search?name=Nicolas%20Maduro&limit=5"

# Batch screening
curl -X POST https://watchman-java.fly.dev/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"id": "1", "name": "John Smith"},
      {"id": "2", "name": "Acme Corp"}
    ],
    "minMatch": 0.85,
    "limit": 10
  }'
```

---

## Troubleshooting

### Common Issues

#### Health Check Failing

```bash
# Check logs for startup errors
flyctl logs | grep -i error

# Verify the app is responding
flyctl ssh console -C "wget -qO- http://localhost:8080/health"
```

#### Out of Memory

```bash
# Increase memory allocation
flyctl scale memory 2048

# Or update fly.toml
[[vm]]
  memory = '2gb'
```

#### Slow Startup

The app downloads sanctions data on startup. This can take 30-60 seconds.

```bash
# Increase health check grace period in fly.toml
[[http_service.checks]]
  grace_period = '120s'
```

#### Data Not Loading

```bash
# Check download status
curl https://watchman-java.fly.dev/v1/download/status

# Manually trigger refresh
curl -X POST https://watchman-java.fly.dev/v1/download/refresh
```

### Rollback

```bash
# List recent deployments
flyctl releases

# Rollback to previous version
flyctl deploy --image <previous-image>
```

### Destroy and Recreate

```bash
# Delete the app (caution!)
flyctl apps destroy watchman-java

# Recreate
flyctl apps create watchman-java
flyctl deploy --remote-only
```

---

## Production Checklist

- [ ] Health check endpoint responding at `/health`
- [ ] All 4 data sources enabled (OFAC, US CSL, EU CSL, UK CSL)
- [ ] Memory set to at least 1GB
- [ ] Grace period sufficient for data download (60-120s)
- [ ] HTTPS enforced (`force_https = true`)
- [ ] Logs accessible via `flyctl logs`
- [ ] Search API returning results
- [ ] Batch API processing requests

---

## Cost Estimate (Fly.io)

| Resource | Specification | Monthly Cost |
|----------|---------------|--------------|
| Machine | 1 shared CPU, 1GB RAM | ~$5-7 |
| Bandwidth | First 100GB free | $0 |
| **Total** | | **~$5-7/month** |

*Costs may vary. Check [Fly.io Pricing](https://fly.io/docs/about/pricing/) for current rates.*

---

## URLs

| Environment | URL |
|-------------|-----|
| Production | https://watchman-java.fly.dev |
| Health | https://watchman-java.fly.dev/health |
| API Docs | https://watchman-java.fly.dev/v2/listinfo |

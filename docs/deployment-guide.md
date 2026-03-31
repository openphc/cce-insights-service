# Deployment Guide

## 1. Overview

The **CCE Insights Service** is a stateless, read-only Spring Boot service that queries the shared `cce_collector` PostgreSQL database and exposes 32 REST endpoints consumed by the Analytics UI dashboard. It requires no Kafka, no Flyway, and no authentication — the CCE Gateway handles OAuth enforcement.

---

## 2. Deployment Options

| Method | Best For |
|--------|----------|
| **Docker Compose** | Local development, single-node staging |
| **Docker** (standalone) | CI/CD pipelines, manual deployments |
| **Kubernetes** | Production, multi-instance HA |
| **Bare-metal / VM** | Legacy infrastructure |

---

## 3. Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 21 LTS | Only for bare-metal; Docker images include JRE |
| PostgreSQL | 16+ | Shared `cce_collector` database — must exist and be populated by Compliance Service |
| Docker | 24+ | For containerized deployments |
| Docker Compose | 2.x | For Compose-based deployments |

---

## 4. Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `SERVER_PORT` | `8084` | No | HTTP port |
| `DB_HOST` | `localhost` | **Yes** | PostgreSQL host |
| `DB_PORT` | `5432` | No | PostgreSQL port |
| `DB_NAME` | `cce_collector` | No | Database name |
| `DB_USERNAME` | `cce_user` | **Yes** | Database username |
| `DB_PASSWORD` | `cce_pass` | **Yes** | Database password |
| `DB_POOL_SIZE` | `10` | No | HikariCP max connections |
| `SPRING_PROFILES_ACTIVE` | — | No | `docker` for containers, `local` for dev |

> **Security:** Never commit credentials. Use environment variables, secrets managers, or Kubernetes Secrets.

---

## 5. Docker Compose Deployment

### 5.1 Quick Start

```bash
# Clone and enter project
git clone <repository-url>
cd cce-insights-service

# Copy and configure environment
cp .env.example .env
# Edit .env with your database credentials

# Start services
docker compose up -d

# Verify
docker compose ps
curl http://localhost:8084/actuator/health
```

### 5.2 Connecting to Existing Database

If the Compliance Service database already exists (e.g., managed by `cce-collector-service`), override the database host:

```bash
docker compose up -d insights-service \
  -e DB_HOST=your-db-host \
  -e DB_PORT=5432 \
  -e DB_USERNAME=cce_user \
  -e DB_PASSWORD=your-password
```

Or simply edit `.env` and omit the `cce-db` service:

```bash
docker compose up -d insights-service
```

---

## 6. Standalone Docker Deployment

### 6.1 Build Image

```bash
docker build -t cce-insights-service:1.0.0 .
```

### 6.2 Run Container

```bash
docker run -d \
  --name cce-insights-service \
  -p 8084:8084 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e DB_HOST=your-db-host \
  -e DB_PORT=5432 \
  -e DB_NAME=cce_collector \
  -e DB_USERNAME=cce_user \
  -e DB_PASSWORD=your-password \
  -e DB_POOL_SIZE=10 \
  cce-insights-service:1.0.0
```

### 6.3 Verify

```bash
# Health check
curl http://localhost:8084/actuator/health

# Test an endpoint
curl http://localhost:8084/v1/deviations?limit=5

# Prometheus metrics
curl http://localhost:8084/actuator/prometheus
```

---

## 7. Kubernetes Deployment

### 7.1 ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: cce-insights-config
  namespace: cce
data:
  SERVER_PORT: "8084"
  DB_HOST: "cce-postgresql"
  DB_PORT: "5432"
  DB_NAME: "cce_collector"
  DB_POOL_SIZE: "10"
  SPRING_PROFILES_ACTIVE: "docker"
```

### 7.2 Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: cce-insights-db-secret
  namespace: cce
type: Opaque
stringData:
  DB_USERNAME: cce_user
  DB_PASSWORD: <your-password>
```

### 7.3 Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cce-insights-service
  namespace: cce
  labels:
    app: cce-insights-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: cce-insights-service
  template:
    metadata:
      labels:
        app: cce-insights-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8084"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: insights-service
          image: cce-insights-service:1.0.0
          ports:
            - containerPort: 8084
          envFrom:
            - configMapRef:
                name: cce-insights-config
            - secretRef:
                name: cce-insights-db-secret
          resources:
            requests:
              memory: "256Mi"
              cpu: "200m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8084
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8084
            initialDelaySeconds: 30
            periodSeconds: 15
```

### 7.4 Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: cce-insights-service
  namespace: cce
spec:
  selector:
    app: cce-insights-service
  ports:
    - port: 8084
      targetPort: 8084
  type: ClusterIP
```

---

## 8. Bare-Metal / VM Deployment

### 8.1 Build

```bash
./gradlew build -x test -x integrationTest
```

### 8.2 Run

```bash
export DB_HOST=your-db-host
export DB_PORT=5432
export DB_USERNAME=cce_user
export DB_PASSWORD=your-password

java -XX:MaxRAMPercentage=75.0 \
     -jar build/libs/cce-insights-service-1.0.0-SNAPSHOT.jar \
     --spring.profiles.active=local
```

### 8.3 Systemd Service (Linux)

```ini
[Unit]
Description=CCE Insights Service
After=network.target postgresql.service

[Service]
Type=simple
User=cce
WorkingDirectory=/opt/cce/insights-service
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=75.0 -jar cce-insights-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
EnvironmentFile=/opt/cce/insights-service/.env
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## 9. Health & Monitoring

### 9.1 Health Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` | Overall health (includes DB connectivity) |
| `GET /actuator/health/readiness` | Kubernetes readiness probe |
| `GET /actuator/health/liveness` | Kubernetes liveness probe |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint |
| `GET /actuator/info` | Application info |

### 9.2 Key Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `cce.insights.request.count` | Counter | Requests by endpoint and status |
| `cce.insights.request.duration` | Timer | Endpoint response time |
| `cce.insights.query.duration` | Timer | Database query execution time |
| `hikaricp_connections_active` | Gauge | Active DB connections |
| `jvm_memory_used_bytes` | Gauge | JVM memory consumption |

### 9.3 Structured Logging

With `SPRING_PROFILES_ACTIVE=docker`, the service outputs JSON-structured logs (via Logstash encoder) suitable for log aggregation tools (ELK, Loki, CloudWatch).

---

## 10. Scaling Considerations

### 10.1 Horizontal Scaling

The service is **fully stateless** — scale to N replicas with no coordination required:
- No Kafka consumer groups
- No local caches
- No session state

### 10.2 Database Connection Pooling

Each instance uses HikariCP with `DB_POOL_SIZE` connections. Ensure:

$$\text{total instances} \times \text{DB\_POOL\_SIZE} \leq \text{PostgreSQL max\_connections (analytics budget)}$$

Example: 3 instances × 10 pool = 30 connections needed.

### 10.3 Read Replicas (Phase 2)

For high-scale deployments, point the Insights Service at a PostgreSQL read replica to eliminate load on the Compliance Service's write primary.

---

## 11. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `503 Service Unavailable` | Database unreachable | Check `DB_HOST`, credentials, network |
| `Connection refused` on 8084 | Service not started | Check logs: `docker logs cce-insights-service` |
| JPA validation errors on startup | Schema mismatch | Ensure Compliance Service migrations have run |
| `OutOfMemoryError` | Insufficient heap | Increase container memory limit |
| Slow queries | Large dataset, no indexes | Check PostgreSQL query plans; consider read replica |

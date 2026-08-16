# DNSFilt Orchestrator 🤖

A lightweight Python microservice responsible for auto-scaling, dynamic cluster health checks, and lifecycle orchestration of the DNSFilt resolver fleet.

---

## 🎯 Features

- **Dynamic Resolver Scaling**: Reconciles actual running resolver instances against desired worker counts stored in the database.
- **Continuous Health Checks**: Probes resolver UDP & HTTP health status across datacenter nodes.
- **Auto-Recovery**: Restarts failed or degraded resolver containers automatically.

---

## 🛠️ Local Development & Deployment

```bash
cd dnsfilt-orchestrator

# Run via Docker Compose
docker compose up --build -d

# View live orchestrator logs
docker logs -f dnsfilt-orchestrator
```

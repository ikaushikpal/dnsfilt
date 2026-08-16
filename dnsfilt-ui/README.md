# DNSFilt UI 🌐

A modern, high-performance web dashboard and administrative console built on **Angular 18**, **Tailwind CSS**, and **Angular SSR / Static Site Generation (SSG)**.

---

## 🎯 Features

- **Real-Time Analytics Dashboard**: Visualizes 24-hour query trends, block rate percentages, cache hit rates, average latency, and category threat breakdowns.
- **Client Risk Telemetry**: Monitors client HMAC anonymized hashes with traffic metrics and automated risk level badges (🟢 Low / 🟡 Medium / 🔴 High).
- **Rule & Domain Governance**: Interactive UI for managing blocklists, whitelist overrides, custom DNS domain records, and category configurations.
- **Resolver Scaling & Version Management**: View and modify live worker replica counts and engine software versions.
- **Multi-OS Configuration Guides**: Step-by-step interactive CLI and network configuration instructions for macOS, Linux, Windows 11/10, PowerShell, and CMD.
- **Dynamic API Auto-Detection**: Dynamically resolves `${window.location.origin}/api` for seamless operation on localhost, OCI Cloud, or behind the Render Proxy.

---

## 🛠️ Local Development

```bash
# 1. Install dependencies
npm ci

# 2. Start Angular dev server
npm start
# Navigate to http://localhost:4200/

# 3. Compile production SSG/SSR bundle
npm run build
```

---

## 🐳 Docker Deployment

```bash
# Build standalone UI container
docker build -t ikaushikpal/dnsfilt-ui:latest .
docker run -p 4200:4200 -d ikaushikpal/dnsfilt-ui:latest
```

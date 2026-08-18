# 🌐 dnsfilt-render-proxy: Cloud Edge Gateway & Firewall Bypass Reverse Proxy

[![Nginx](https://img.shields.io/badge/Nginx-1.25%20Alpine-brightgreen?style=flat-square&logo=nginx)](https://nginx.org/)
[![Render](https://img.shields.io/badge/Deployed%20on-Render-46E3B7?style=flat-square&logo=render)](https://render.com)
[![Docker Multi-Arch](https://img.shields.io/badge/Docker-AMD64%20%7C%20ARM64-blue?style=flat-square&logo=docker)](https://hub.docker.com/r/ikaushikpal/dnsfilt-render-proxy)

`dnsfilt-render-proxy` is a high-performance, lightweight **Nginx** reverse proxy deployed globally on Render (`https://dnsfilt-proxy.onrender.com`). It acts as a trusted cloud ingress gateway that transparently forwards traffic to the primary production host at `https://dnsfilt.mooo.com`.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I built `dnsfilt-render-proxy` to solve a real-world enterprise problem: corporate firewalls (Zscaler, Fortinet, Palo Alto) frequently block dynamic DNS domains (`.mooo.com`). Routing through Render's trusted CDN infrastructure guarantees frictionless global access.
>
> 🔍 **I am actively seeking new engineering opportunities.** If you value practical problem solving, networking expertise, and production-first cloud engineering, please reach out to me on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star ⭐, issue, or referral means a lot — thank you!*

---

## 💡 What is `dnsfilt-render-proxy`?

`dnsfilt-render-proxy` provides a secure, trusted public URL for DNSFilt. It terminates TLS on Render's global edge network, injects standard MIME types, handles WebSocket upgrades, and forwards all requests to the backend server with Server Name Indication (SNI) validation.

### Key Capabilities:
- **🛡️ Enterprise Firewall Bypass**: Allows corporate users behind strict enterprise proxies to access the DNSFilt web console and APIs.
- **⚡ Dynamic Runtime Configuration**: Uses `envsubst` to dynamically populate upstream targets and port bindings on boot.
- **🔄 Zero-Upstream `/healthz` Probe**: Instant health check endpoint for zero-overhead uptime monitoring and Render deploy hooks.
- **📦 MIME Type Ingestion**: Explicitly loads `/etc/nginx/mime.types` so modern JavaScript ES modules (`.mjs`, `.js`) and CSS load with correct `Content-Type` headers.

---

## 🎯 Why `dnsfilt-render-proxy`?

```text
Enterprise Client / Zscaler Network / VPN
                  │
                  ▼ HTTPS
https://dnsfilt-proxy.onrender.com    ← Render Cloud (Globally Whitelisted CDN)
                  │
                  ▼ proxy_pass + SNI (HTTPS)
https://dnsfilt.mooo.com              ← Primary Production Host (Oracle Cloud)
```

---

## 🚀 How to Run

### 1. Run Locally with Docker
```bash
cd dnsfilt-render-proxy

docker run -d \
  --name dnsfilt-render-proxy \
  -p 8080:80 \
  -e PORT=80 \
  -e UPSTREAM_HOST=dnsfilt.mooo.com \
  -e UPSTREAM_PORT=443 \
  ikaushikpal/dnsfilt-render-proxy:latest
```

### 2. Multi-Arch Docker Build & Push
```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ikaushikpal/dnsfilt-render-proxy:latest \
  --push .
```

---

## 🔧 Troubleshooting Guide

### 1. `502 Bad Gateway` on Render Proxy
- **Cause**: Upstream host (`dnsfilt.mooo.com`) is unreachable, or the SSL certificate on OCI has expired.
- **Fix**: Check that the backend server is running and port 443 is open:
  ```bash
  curl -Iv https://dnsfilt.mooo.com/actuator/health
  ```

### 2. Initial Request Takes 30–50 Seconds (Cold Start)
- **Cause**: Render's free tier spins down idle instances after 15 minutes of inactivity.
- **Fix**: Set up an uptime ping service (e.g. UptimeRobot or cron) to hit `https://dnsfilt-proxy.onrender.com/healthz` every 10 minutes.

### 3. JavaScript Files Blocked Due to Wrong MIME Type (`text/plain`)
- **Cause**: Nginx template missing `/etc/nginx/mime.types`.
- **Fix**: The proxy includes `include /etc/nginx/mime.types;` and `default_type application/octet-stream;`, ensuring all Angular ES modules serve with `application/javascript`.

---

## 📄 License
Licensed under the [MIT License](../LICENSE).

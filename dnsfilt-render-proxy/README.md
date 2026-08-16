# DNSFilt Render Gateway Proxy 🌐

A lightweight Nginx reverse-proxy deployed on <a href="https://render.com" target="_blank" rel="noopener noreferrer">Render</a> (`https://dnsfilt-proxy.onrender.com`) that transparently forwards all traffic to the primary production host at `https://dnsfilt.mooo.com`.

---

## 🎯 Purpose & Firewall Bypass Strategy

### Problem

Corporate networks, enterprise VPNs, and security gateways (**Zscaler**, **Palo Alto Networks**, **Fortinet**, **BlueCoat**) frequently flag or outright block `.mooo.com` and custom dynamic DNS domains under strict security policies. Users and administrators behind these networks cannot reach the DNSFilt Admin Dashboard or APIs.

### Solution

This proxy runs on Render's trusted global CDN infrastructure. Render's domain (`onrender.com`) is whitelisted by virtually every corporate firewall. All traffic is forwarded 1:1 to the real backend on Oracle Cloud Infrastructure (OCI).

```text
Enterprise Client / Zscaler Network
           │
           ▼  HTTPS
https://dnsfilt-proxy.onrender.com    ← Render Cloud (trusted domain)
           │
           ▼  proxy_pass + SNI
https://dnsfilt.mooo.com              ← Primary OCI Production Host
```

---

## ⚙️ How It Works

**`start.sh`** substitutes the `$PORT`, `$UPSTREAM_HOST`, and `$UPSTREAM_PORT` environment variables (provided by Render at runtime) into `nginx.conf.template`, then starts Nginx in the foreground:

```sh
envsubst '${PORT} ${UPSTREAM_HOST} ${UPSTREAM_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
exec nginx -g "daemon off;"
```

**`nginx.conf.template`** proxies everything to the upstream with:

- `proxy_ssl_server_name on` — sends the correct SNI header so the OCI Nginx can match the right SSL virtual host
- `Host: ${UPSTREAM_HOST}` — ensures the backend sees the correct hostname
- `proxy_read_timeout 300` — allows long analytical queries and batch rollups to complete without timing out
- WebSocket upgrade headers (`$http_upgrade`, `$connection_upgrade`) for live real-time connections
- `/healthz` endpoint for fast, zero-upstream health checks by Render and uptime monitors

---

## 🐳 Docker Build & Push

### Automated (CI/CD)

Every push to `main` that touches `dnsfilt-render-proxy/` triggers the GitHub Actions workflow <a href="../.github/workflows/release-render-proxy.yaml" target="_blank" rel="noopener noreferrer">release-render-proxy.yaml</a>, which builds and pushes `ikaushikpal/dnsfilt-render-proxy:latest` to Docker Hub. Render auto-deploys from there.

### Manual Multi-Arch Build

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ikaushikpal/dnsfilt-render-proxy:latest \
  --push .
```

---

## ⚠️ What Breaks If the SSL Certificate on `dnsfilt.mooo.com` Expires

This proxy talks to the backend **over HTTPS**. Nginx verifies the upstream TLS certificate by default. If `dnsfilt.mooo.com`'s certificate expires or becomes invalid, the following cascade of failures occurs:

| Layer | What Breaks | Symptom |
|---|---|---|
| **Render proxy → OCI** | Nginx upstream SSL handshake fails | Proxy returns `502 Bad Gateway` to all users |
| **Enterprise Users** | Render proxy is their only route | Entire dashboard & API is unreachable for corporate users |
| **Direct Users** | Browser rejects the expired cert | `NET::ERR_CERT_DATE_INVALID` in browser |
| **Microservices / UI** | Frontend SSR calls fail over HTTPS | `PrematureCloseException`, UI displays blank / disconnected |
| **CI/CD Health Checks** | GitHub Actions / Render deploy hooks fail | Deployments may be rejected as unhealthy |
| **API Clients** | Any HTTPS client with strict cert validation | `SSLHandshakeException` / connection refused |

---

## 🔧 Troubleshooting

### Render proxy returns `502 Bad Gateway`

The proxy successfully received the request but couldn't reach `dnsfilt.mooo.com`.

**Check 1 — Is the backend up?**

```bash
curl -Iv https://dnsfilt.mooo.com/actuator/health
```

**Check 2 — Is the SSL cert valid?**

```bash
curl -vI https://dnsfilt.mooo.com 2>&1 | grep -E "SSL|expire|issuer|subject"

# Or check expiry directly
echo | openssl s_client -connect dnsfilt.mooo.com:443 -servername dnsfilt.mooo.com 2>/dev/null \
  | openssl x509 -noout -dates
```

**Check 3 — Is the OCI VM reachable?**

```bash
# Ping OCI host
ping dnsfilt.mooo.com

# Check if port 443 is open
nc -zv dnsfilt.mooo.com 443
```

---

### Proxy works but responses are very slow

Render's free tier **spins down** after 15 minutes of inactivity. The first request after spin-down can take 30–60 seconds to cold start. This is a Render free-tier limitation — not a bug.

- Upgrade to a paid Render plan for always-on instances, or
- Use an uptime monitor (e.g., UptimeRobot) to ping the `/healthz` endpoint every 10 minutes to prevent spin-down.

---

### `NET::ERR_CERT_AUTHORITY_INVALID` or `SSL_ERROR_RX_RECORD_TOO_LONG`

The backend SSL cert has expired or the OCI Nginx is serving on the wrong port.

```bash
# Check what's listening on port 443 on the OCI server
sudo ss -tlnp | grep 443

# Test cert validity
sudo openssl x509 \
  -in /etc/letsencrypt/live/dnsfilt.mooo.com/fullchain.pem \
  -noout -dates
```

---

### Render deployment is stuck or failing

```bash
# 1. Check Docker Hub for the latest image
#    https://hub.docker.com/r/ikaushikpal/dnsfilt-render-proxy/tags

# 2. Trigger a manual redeploy from the Render dashboard
#    Dashboard → Service → Manual Deploy → Deploy latest commit

# 3. Check GitHub Actions for build failures
#    Repository → Actions → release-render-proxy workflow
```

---

### WebSocket connections dropping through the proxy

The `nginx.conf.template` already sets the required `Upgrade` and `Connection` headers. If WebSocket connections still drop:

1. Confirm `proxy_read_timeout 300` is in the config (it is by default).
2. Check that the client is connecting to `wss://dnsfilt-proxy.onrender.com` (not `ws://`).
3. Render free tier may terminate idle WebSocket connections — consider a paid tier.

---

### The proxy URL works but the `.mooo.com` URL does not

This confirms the issue is on the OCI server side or corporate DNS blocking, not the proxy. Common causes:

| Cause | Fix |
|---|---|
| Nginx on OCI is stopped | `sudo systemctl start nginx` |
| Cert expired → Nginx won't start | `sudo systemctl restart nginx` after renewing Let's Encrypt cert |
| OCI firewall blocks port 443 | Check VCN Security List — allow TCP 443 ingress |
| Backend container crashed | `sudo docker ps -a` — restart if exited |
| Nginx upstream points to wrong port | `cat /etc/nginx/conf.d/dnsfilt-backend.conf` |

---

## 🗂️ File Reference

Whenever changes are merged into the `main` branch, GitHub Actions automatically triggers <a href="../.github/workflows/release-render-proxy.yaml" target="_blank" rel="noopener noreferrer">.github/workflows/release-render-proxy.yaml</a> to compile, tag, and publish `ikaushikpal/dnsfilt-render-proxy:latest` to Docker Hub. Render then automatically deploys the updated image via webhook or polling.

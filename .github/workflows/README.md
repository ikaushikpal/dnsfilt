# ⚙️ GitHub Actions CI/CD Workflows

This directory contains the automated CI/CD pipelines, quality gates, and release automation for all **DNSFilt** microservices and web components.

---

## 📋 Microservice Release Workflows

| Microservice | Workflow File | Trigger Path | Docker Hub Target |
|---|---|---|---|
| **DNS Resolver Engine** | [`release-dnsfilt-resolver.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/release-dnsfilt-resolver.yaml) | `dnsfilt-resolver/**` | `ikaushikpal/dnsfilt-resolver` |
| **Admin Backend & API Gateway** (Bundles UI) | [`release-dnsfilt-admin-backend.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/release-dnsfilt-admin-backend.yaml) | `dnsfilt-admin-backend/**`, `dnsfilt-ui/**` | `ikaushikpal/dnsfilt-admin-backend` |
| **Analytics & Retention Engine** | [`release-dnsfilt-analytics.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/release-dnsfilt-analytics.yaml) | `dnsfilt-analytics/**` | `ikaushikpal/dnsfilt-analytics` |
| **Render Gateway Proxy** | [`release-render-proxy.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/release-render-proxy.yaml) | `dnsfilt-render-proxy/**` | `ikaushikpal/dnsfilt-render-proxy` |
| **PR Quality Gate** | [`pr-quality-check.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/pr-quality-check.yaml) | PRs targeting `main` | Lint, typecheck & build tests |
| **Unified Release on Main** | [`release-docker-on-main.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/release-docker-on-main.yaml) | Direct push / merge to `main` | Multi-arch matrix release |

---

## 🏷️ Automated SemVer Tagging Logic

Workflows calculate Semantic Versioning (SemVer) tags automatically based on Conventional Commits:

| PR Title / Commit Prefix | SemVer Bump Type | Example (`1.0.0` Previous) |
|---|---|---|
| `feat:` / `feat(...)` | **Minor** | `1.0.0` → `1.1.0` |
| `fix:` / `chore:` / `refactor:` | **Patch** | `1.0.0` → `1.0.1` |
| `feat!:` / `BREAKING CHANGE` | **Major** | `1.0.0` → `2.0.0` |

---

## 🐳 Docker Multi-Architecture Builds

Every workflow uses **Docker Buildx** and **QEMU** to build and push multi-architecture images:
- **Supported Architectures**: `linux/amd64` (Intel/AMD x86_64) and `linux/arm64` (Apple Silicon & ARM Cloud Instances)
- **Layer Caching**: GitHub Actions Cache (`type=gha`) for fast incremental builds
- **Tags Pushed to Docker Hub**:
  - `ikaushikpal/<service-name>:latest`
  - `ikaushikpal/<service-name>:<version>`
  - `ikaushikpal/<service-name>:v<version>`

---

## 🔐 Required GitHub Secrets

Go to **Repository → Settings → Secrets and variables → Actions** and configure:

| Secret Name | Purpose | Example |
|---|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub Account Username | `ikaushikpal` |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token (Read/Write) | `dckr_pat_...` |

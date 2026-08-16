# ⚙️ GitHub Actions CI/CD Workflows

This directory contains the unified CI/CD pipelines, quality gates, and multi-architecture release automation for **DNSFilt**.

---

## 📋 Active Workflows

| Workflow | Trigger | Description |
|---|---|---|
| [`pr-quality-check.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/pr-quality-check.yaml) | PRs targeting `main` | Semantic PR title verification, Angular frontend build/typechecks, and Java microservices compilation (`gradle build -x test`). |
| [`python-scripts-check.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/python-scripts-check.yaml) | PRs touching `scripts/**` | Byte-compilation and CLI verification under Python 3.9 runtime. |
| [`release-docker-on-main.yaml`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/.github/workflows/release-docker-on-main.yaml) | Direct push / merge to `main` | Computes **ONE single SemVer tag** (`vX.Y.Z`), creates **ONE Git tag**, and builds/pushes multi-arch Docker images (`linux/amd64`, `linux/arm64`) to Docker Hub. |

---

## 🐳 Published Multi-Arch Docker Hub Images

| Image | Context Directory | Supported Platforms |
|---|---|---|
| `ikaushikpal/dnsfilt-resolver` | `dnsfilt-resolver` | `linux/amd64`, `linux/arm64` |
| `ikaushikpal/dnsfilt-analytics` | `dnsfilt-analytics` | `linux/amd64`, `linux/arm64` |
| `ikaushikpal/dnsfilt-admin-backend` | `dnsfilt-admin-backend` (bundles UI) | `linux/amd64`, `linux/arm64` |
| `ikaushikpal/dnsfilt-render-proxy` | `dnsfilt-render-proxy` | `linux/amd64`, `linux/arm64` |

---

## 🏷️ Version Tagging

On every merge to `main`, exactly **one Git tag** is created (`vX.Y.Z`).
Docker images are pushed to Docker Hub with tags:
- `latest` (tracks the most recent build)
- `v<version>` (immutable version tag matching Git, e.g. `v0.0.4`)

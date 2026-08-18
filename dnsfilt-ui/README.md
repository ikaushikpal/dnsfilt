# 🎨 dnsfilt-ui: High-Performance Angular 18 Web Console & Dashboard

[![Angular 18](https://img.shields.io/badge/Angular-18.0-red?style=flat-square&logo=angular)](https://angular.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.4-blue?style=flat-square&logo=typescript)](https://www.typescriptlang.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-CSS%203.4-38B2AC?style=flat-square&logo=tailwind-css)](https://tailwindcss.com/)
[![Chart.js](https://img.shields.io/badge/Chart.js-4.4-FF6384?style=flat-square&logo=chartdotjs)](https://www.chartjs.org/)

`dnsfilt-ui` is the modern, responsive web console for the DNSFilt platform. Built with **Angular 18**, **Angular Signals**, **Tailwind CSS**, and modern glassmorphism design principles, it delivers real-time DNS telemetry, threat governance, cluster auto-scaling, and client management.

---

## 👋 A Note from the Author

> Hi! I'm **Kaushik**, the developer behind **DNSFilt**. I crafted `dnsfilt-ui` to combine aesthetic excellence with developer ergonomics — using reactive Angular 18 Signals, zero-flicker single-button auth states, and dark-mode glassmorphic visuals.
>
> 🔍 **I am actively seeking new engineering opportunities.** If you appreciate thoughtful frontend architecture, clean component design, and attention to detail, let's connect on [**LinkedIn**](https://www.linkedin.com/in/ikaushikpal).
>
> ⭐ *Every star ⭐, issue, or referral means a lot — thank you!*

---

## 💡 What is `dnsfilt-ui`?

`dnsfilt-ui` is the single-pane-of-glass administrative frontend for DNSFilt. It communicates with `dnsfilt-admin-backend` via typed REST APIs to deliver security observability, threat mitigation, domain management, and cluster scaling.

### Key Capabilities:
- **📊 Real-Time Telemetry Dashboard**: Visualizes 24-hour QPS volume, block rates, cache hit ratios, and average latency with smooth Chart.js analytics.
- **⚡ Reactive Angular Signals**: Powered by native Angular 18 Signals for fine-grained reactivity and minimal change-detection overhead.
- **🎛️ 1-Click Cluster Upgrade & Scaling**: Dynamic +/- worker node scaling and an automated **"Upgrade to Latest"** rolling release trigger.
- **🔒 Dedicated SuperAdmin Management**: User creation, role assignments (`ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER`), and self-service password updates with eye visibility toggles.
- **🕒 Scoped Navbar & Live UTC Clock**: Clean separation between public pages (Home, CLI, Learn) and private Dashboard views with a live UTC clock (`YYYY-MM-DD HH:mm:ss UTC`).
- **💻 Interactive Multi-OS CLI Setup**: Step-by-step interactive DNS setup guides with 1-click copy buttons for macOS, Linux, Windows 11/10, PowerShell, and CMD.

---

## 🎯 Why `dnsfilt-ui`?

| Challenge | Traditional Angular Approach | `dnsfilt-ui` Solution |
|---|---|---|
| **Hydration Flicker** | Dual `@if` / `@else` auth buttons causing layout shift on page reload. | **Single Unified `<button>`**: Dynamic label and action bindings based directly on `localStorage` token presence. |
| **API Port Hardcoding** | Hardcoded `localhost:8080` breaking on custom ports or behind proxies. | **Tiered Port Resolution**: Injects `environment.backendPort` in dev (`4200`) and automatically inherits `window.location.origin` in production. |
| **Form Poll Interference** | Background polling wiping active form inputs. | **Decoupled Input Signals**: Separates mutable form state from periodic background metrics polls. |

---

## 🚀 How to Run

### 1. Local Development
```bash
cd dnsfilt-ui

# 1. Install dependencies
npm ci

# 2. Run Angular dev server
npm start
# Navigate to http://localhost:4200
```

### 2. Environment Configuration
Configure custom backend ports in [`src/environments/environment.ts`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-ui/src/environments/environment.ts):

```typescript
export const environment = {
  production: false,
  backendPort: 9090, // Set to 8080, 9090, or your local backend port
  apiUrl: ''         // Optional full URL override
};
```

### 3. Production Build
```bash
# Compiles production bundle to dist/dnsfilt-ui/browser/
npm run build
```
*(Note: When running `gradle build` in `dnsfilt-admin-backend`, the backend automatically compiles and copies this bundle into `src/main/resources/static/`).*

---

## 🔧 Troubleshooting Guide

### 1. Browser Loads Stale JavaScript Chunks (404 on `chunk-*.js`)
- **Cause**: Browser aggressively cached an older `index.html` referencing outdated chunk hashes.
- **Fix**: Perform a hard refresh: **`Ctrl + Shift + R`** (Windows/Linux) or **`Cmd + Shift + R`** (Mac), or test in an Incognito window.

### 2. UI Connects to Wrong Backend Port in Development
- **Cause**: Angular dev server running on `4200` defaulting to `9090`.
- **Fix**: Update `backendPort` in [`src/environments/environment.ts`](file:///Users/kaushikpal/Desktop/codes/projects/dnsfilt/dnsfilt-ui/src/environments/environment.ts), or set `localStorage.setItem('dnsfilt_backend_port', '8080')` in your browser console.

---

## 📄 License
Licensed under the [MIT License](../LICENSE).

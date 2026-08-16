import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="space-y-8 font-sans">
      <div>
        <h2 class="text-2xl font-mono font-bold text-white flex items-center gap-2">
          <span>ℹ️</span> About DNSFilt Platform
        </h2>
        <p class="text-stone-400 text-xs font-mono mt-1">
          High-performance microservices architecture for DNS security and query telemetry
        </p>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <!-- Service 1 -->
        <div class="bg-stone-900/60 border border-stone-800 rounded-xl p-6 space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="font-mono font-bold text-amber-400 text-sm">dnsfilt-resolver</h3>
            <span class="bg-stone-950 text-emerald-400 text-[10px] font-mono px-2 py-0.5 rounded border border-stone-800">Port 2053</span>
          </div>
          <p class="text-xs text-stone-300 leading-relaxed">
            Java 21 UDP Engine using Virtual Threads, Caffeine L1 cache, Aiven Valkey Redis L2 cache, and Aiven Kafka event producer.
          </p>
        </div>

        <!-- Service 2 -->
        <div class="bg-stone-900/60 border border-stone-800 rounded-xl p-6 space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="font-mono font-bold text-amber-400 text-sm">dnsfilt-admin-backend</h3>
            <span class="bg-stone-950 text-emerald-400 text-[10px] font-mono px-2 py-0.5 rounded border border-stone-800">Port 8080</span>
          </div>
          <p class="text-xs text-stone-300 leading-relaxed">
            Spring Boot 3 REST API providing JWT Authentication, Refresh Token security, Admin User Management, and Domain Blocklist Rule management.
          </p>
        </div>

        <!-- Service 3 -->
        <div class="bg-stone-900/60 border border-stone-800 rounded-xl p-6 space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="font-mono font-bold text-amber-400 text-sm">dnsfilt-analytics</h3>
            <span class="bg-stone-950 text-emerald-400 text-[10px] font-mono px-2 py-0.5 rounded border border-stone-800">Port 8081</span>
          </div>
          <p class="text-xs text-stone-300 leading-relaxed">
            Dedicated microservice consuming Kafka query log stream (<code class="text-amber-300 font-mono">dns-query-logs</code>) and exposing live analytics & query stream APIs.
          </p>
        </div>
      </div>
    </section>
  `
})
export class AboutComponent {}

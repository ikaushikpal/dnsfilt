import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, ResolverConfig, SummaryStats } from '../../services/api.service';

export interface ResolverNode {
  resolverId: string;
  status: 'Online' | 'Offline' | 'Scaling';
  statusBadge: string;
  qps: number;
  cacheHitPercent: number;
  p95Latency: string;
  lastHeartbeat: string;
}

@Component({
  selector: 'app-resolvers',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './resolvers.component.html',
  styleUrl: './resolvers.component.css'
})
export class ResolversComponent implements OnInit, OnDestroy {
  summary = signal<SummaryStats>({
    totalQueries: 0,
    blockedQueries: 0,
    blockRatePercent: 0,
    cacheHitPercent: 0,
    avgLatencyMs: 0,
    activeClients: 0
  });

  desiredCount = signal<number>(3);
  desiredVersion = signal<string>('1.0.0');

  updatingCount = signal<boolean>(false);
  updatingVersion = signal<boolean>(false);
  configSuccessMessage = signal<string | null>(null);

  resolversData = signal<ResolverNode[]>([]);
  private refreshInterval: any;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadData();
    this.refreshInterval = setInterval(() => this.loadData(), 4000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }

  loadData(): void {
    this.apiService.getSummary().subscribe({
      next: s => { if (s) this.summary.set(s); },
      error: () => {}
    });

    this.apiService.getResolverConfig().subscribe({
      next: cfg => {
        if (cfg) {
          this.desiredCount.set(cfg.desiredCount);
          this.desiredVersion.set(cfg.desiredVersion);
          this.generateResolverNodes(cfg.desiredCount);
        }
      },
      error: () => {
        this.generateResolverNodes(this.desiredCount());
      }
    });
  }

  onUpdateCount(newCount: number): void {
    if (newCount < 1 || newCount > 20) return;
    this.updatingCount.set(true);
    this.apiService.updateResolverCount(newCount).subscribe({
      next: cfg => {
        this.updatingCount.set(false);
        this.desiredCount.set(cfg.desiredCount);
        this.generateResolverNodes(cfg.desiredCount);
        this.showSuccess(`Resolver cluster scaled to ${cfg.desiredCount} desired instances.`);
      },
      error: () => {
        this.updatingCount.set(false);
      }
    });
  }

  onUpdateVersion(): void {
    const v = this.desiredVersion().trim();
    if (!v) return;
    this.updatingVersion.set(true);
    this.apiService.updateResolverVersion(v).subscribe({
      next: cfg => {
        this.updatingVersion.set(false);
        this.desiredVersion.set(cfg.desiredVersion);
        this.showSuccess(`Resolver desired version updated to v${cfg.desiredVersion}.`);
      },
      error: () => {
        this.updatingVersion.set(false);
      }
    });
  }

  private showSuccess(msg: string): void {
    this.configSuccessMessage.set(msg);
    setTimeout(() => this.configSuccessMessage.set(null), 3500);
  }

  private generateResolverNodes(count: number): void {
    const s = this.summary();
    const totalQps = s.totalQueries > 0 ? Math.round(s.totalQueries / 86400) : 0;
    const avgCacheHit = s.cacheHitPercent || 84.2;

    const nodes: ResolverNode[] = [];
    for (let i = 1; i <= count; i++) {
      const idStr = String(i).padStart(2, '0');
      const nodeQps = totalQps > 0 ? Math.round(totalQps / count) : 0;
      nodes.push({
        resolverId: `dnsfilt-resolver-${idStr}`,
        status: 'Online',
        statusBadge: '🟢',
        qps: nodeQps,
        cacheHitPercent: Math.round(avgCacheHit),
        p95Latency: s.avgLatencyMs > 0 ? `${Math.round(s.avgLatencyMs * 1.5)}ms` : '< 1ms',
        lastHeartbeat: 'Live active'
      });
    }
    this.resolversData.set(nodes);
  }
}

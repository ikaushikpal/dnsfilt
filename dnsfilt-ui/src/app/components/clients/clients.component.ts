import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, TopClient, TrafficPoint, CategoryBreakdown, TopBlockedDomain } from '../../services/api.service';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.css'
})
export class ClientsComponent implements OnInit {
  searchQuery = signal<string>('');

  liveTopClients = signal<TopClient[]>([]);
  trafficData = signal<TrafficPoint[]>([]);
  categoriesData = signal<CategoryBreakdown[]>([]);
  topBlockedData = signal<TopBlockedDomain[]>([]);

  selectedClient = signal<string | null>(null);

  filteredClients = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const all = this.liveTopClients();
    if (!q) return all;
    return all.filter(c => 
      c.clientHash.toLowerCase().includes(q) ||
      c.riskLevel.toLowerCase().includes(q)
    );
  });

  selectedClientInfo = computed(() => {
    const hash = this.selectedClient();
    if (!hash) return null;
    const found = this.liveTopClients().find(c => c.clientHash.toLowerCase() === hash.toLowerCase());
    if (found) return found;

    return {
      clientHash: hash,
      totalQueries: 1,
      blockedQueries: 0,
      blockRate: 0,
      distinctDomains: 1,
      riskLevel: 'LOW',
      riskBadge: '🟢'
    } as TopClient;
  });

  clientActivityData = computed(() => {
    const tf = this.trafficData();
    const clientCount = Math.max(this.liveTopClients().length, 1);
    if (tf && tf.length > 0) {
      return tf.map(t => ({
        hour: t.time,
        queries: Math.round(t.totalQueries / clientCount)
      }));
    }
    return [];
  });

  clientCategoriesData = computed(() => {
    const cats = this.categoriesData();
    const clientCount = Math.max(this.liveTopClients().length, 1);
    if (cats && cats.length > 0) {
      return cats.map(c => ({
        category: c.category,
        queries: Math.round(c.totalQueries / clientCount),
        blocked: Math.round(c.blockedQueries / clientCount),
        pctBlocked: c.totalQueries > 0 ? Math.round((c.blockedQueries * 100) / c.totalQueries) : 0
      }));
    }
    return [];
  });

  clientTopDomainsData = computed(() => {
    const domains = this.topBlockedData();
    if (domains && domains.length > 0) {
      return domains.map((d, index) => ({
        rank: index + 1,
        domain: d.domain,
        queries: d.requests,
        blocked: d.blockedRequests
      }));
    }
    return [];
  });

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadLiveData();
  }

  loadLiveData(): void {
    this.apiService.getTopClients().subscribe({
      next: (clients) => {
        if (clients && clients.length > 0) {
          this.liveTopClients.set(clients);
        }
      },
      error: () => {}
    });

    this.apiService.getTraffic().subscribe({
      next: (t) => { if (t) this.trafficData.set(t); },
      error: () => {}
    });

    this.apiService.getCategoriesBreakdown().subscribe({
      next: (c) => { if (c) this.categoriesData.set(c); },
      error: () => {}
    });

    this.apiService.getTopBlocked().subscribe({
      next: (b) => { if (b) this.topBlockedData.set(b); },
      error: () => {}
    });
  }

  onSearchSubmit(): void {
    const q = this.searchQuery().trim();
    if (!q) {
      this.selectedClient.set(null);
      return;
    }

    if (/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(q)) {
      const hash = this.simpleHashIp(q);
      this.selectedClient.set(hash);
    } else {
      this.selectedClient.set(q);
    }
  }

  selectClient(hash: string): void {
    if (this.selectedClient() === hash) {
      this.selectedClient.set(null);
    } else {
      this.selectedClient.set(hash);
      this.searchQuery.set(hash);
    }
  }

  clearSelection(): void {
    this.selectedClient.set(null);
    this.searchQuery.set('');
  }

  private simpleHashIp(ip: string): string {
    let hash = 0;
    for (let i = 0; i < ip.length; i++) {
      hash = (hash << 5) - hash + ip.charCodeAt(i);
      hash |= 0;
    }
    const hex = Math.abs(hash).toString(16).padStart(8, '0');
    return `${hex}c7191c01`.substring(0, 16);
  }
}

import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, SummaryStats, TrafficPoint, CategoryBreakdown, TopBlockedDomain, TopClient } from '../../services/api.service';
import { RefreshService } from '../../services/refresh.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {
  // Preset or custom filter
  selectedTimeRange = signal<'1H' | '24H' | '7D' | '30D' | 'MONTH' | 'CUSTOM'>('24H');
  selectedMonth = signal<string>('2026-08');
  startDate = signal<string>('2026-08-01');
  endDate = signal<string>('2026-08-16');
  selectedGranularity = signal<'HOURLY' | 'DAILY'>('HOURLY');

  // Available past months for picker
  availableMonths = [
    { label: 'August 2026 (Current)', value: '2026-08' },
    { label: 'July 2026', value: '2026-07' },
    { label: 'June 2026', value: '2026-06' },
    { label: 'May 2026', value: '2026-05' },
    { label: 'April 2026', value: '2026-04' },
    { label: 'March 2026', value: '2026-03' },
    { label: 'February 2026', value: '2026-02' },
    { label: 'January 2026', value: '2026-01' },
    { label: 'December 2025', value: '2025-12' },
    { label: 'November 2025', value: '2025-11' }
  ];

  summary = signal<SummaryStats>({
    totalQueries: 0,
    blockedQueries: 0,
    blockRatePercent: 0,
    cacheHitPercent: 0,
    avgLatencyMs: 0,
    activeClients: 0
  });

  trafficData = signal<TrafficPoint[]>([]);
  categoryData = signal<CategoryBreakdown[]>([]);
  topBlockedData = signal<TopBlockedDomain[]>([]);
  topClientsData = signal<TopClient[]>([]);

  // Pagination for Top Blocked Domains (10 per page)
  topBlockedPage = signal<number>(1);
  topBlockedPageSize = signal<number>(10);

  totalTopBlockedPages = computed(() => {
    return Math.max(1, Math.ceil(this.topBlockedData().length / this.topBlockedPageSize()));
  });

  paginatedTopBlockedData = computed(() => {
    const page = this.topBlockedPage();
    const size = this.topBlockedPageSize();
    const start = (page - 1) * size;
    return this.topBlockedData().slice(start, start + size);
  });

  // Hover/active tooltip bar for mobile touch interaction
  activePoint = signal<TrafficPoint | null>(null);

  private refreshInterval: any;
  private refreshSub?: Subscription;

  maxCategoryBlocked = computed(() => {
    const cats = this.categoryData();
    if (!cats || cats.length === 0) return 1;
    return Math.max(...cats.map(c => c.blockedQueries || 0), 1);
  });

  maxTrafficTotal = computed(() => {
    const tf = this.trafficData();
    if (!tf || tf.length === 0) return 100;
    return Math.max(...tf.map(t => t.totalQueries || 0), 100);
  });

  constructor(
    private apiService: ApiService,
    private refreshService: RefreshService
  ) {}

  ngOnInit(): void {
    this.loadLiveData();
    this.refreshInterval = setInterval(() => {
      // Auto-poll only on real-time modes (1H or 24H)
      if (this.selectedTimeRange() === '1H' || this.selectedTimeRange() === '24H') {
        this.loadLiveData();
      }
    }, 5000);

    this.refreshSub = this.refreshService.refresh$.subscribe(() => {
      this.loadLiveData();
    });
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
    if (this.refreshSub) this.refreshSub.unsubscribe();
  }

  prevTopBlockedPage(): void {
    if (this.topBlockedPage() > 1) {
      this.topBlockedPage.update(p => p - 1);
    }
  }

  nextTopBlockedPage(): void {
    if (this.topBlockedPage() < this.totalTopBlockedPages()) {
      this.topBlockedPage.update(p => p + 1);
    }
  }

  getCategoryColor(category: string): string {
    const cat = (category || '').toUpperCase();
    if (cat.includes('MALWARE') || cat.includes('PHISH') || cat.includes('SECURITY') || cat.includes('THREAT')) return 'bg-rose-500';
    if (cat.includes('AD') || cat.includes('PROMO')) return 'bg-[rgb(230,130,16)]';
    if (cat.includes('TRACK') || cat.includes('ANALYTICS') || cat.includes('TELEMETRY')) return 'bg-indigo-500';
    if (cat.includes('CRYPTO') || cat.includes('MINING')) return 'bg-cyan-500';
    if (cat.includes('GAMBLING') || cat.includes('BET')) return 'bg-amber-400';
    if (cat.includes('ADULT') || cat.includes('PORN')) return 'bg-purple-500';
    if (cat.includes('SOCIAL')) return 'bg-blue-500';
    return 'bg-emerald-500';
  }

  getCategoryTextColor(category: string): string {
    const cat = (category || '').toUpperCase();
    if (cat.includes('MALWARE') || cat.includes('PHISH') || cat.includes('SECURITY') || cat.includes('THREAT')) return 'text-rose-400';
    if (cat.includes('AD') || cat.includes('PROMO')) return 'text-[rgb(230,130,16)]';
    if (cat.includes('TRACK') || cat.includes('ANALYTICS') || cat.includes('TELEMETRY')) return 'text-indigo-400';
    if (cat.includes('CRYPTO') || cat.includes('MINING')) return 'text-cyan-400';
    if (cat.includes('GAMBLING') || cat.includes('BET')) return 'text-amber-400';
    if (cat.includes('ADULT') || cat.includes('PORN')) return 'text-purple-400';
    if (cat.includes('SOCIAL')) return 'text-blue-400';
    return 'text-emerald-400';
  }

  onRangeChange(range: string): void {
    const validRange = range as ('1H' | '24H' | '7D' | '30D' | 'MONTH' | 'CUSTOM');
    this.selectedTimeRange.set(validRange);
    if (validRange === 'MONTH' || validRange === '30D') {
      this.selectedGranularity.set('DAILY');
    } else if (validRange === '1H' || validRange === '24H') {
      this.selectedGranularity.set('HOURLY');
    }
    this.loadLiveData();
  }

  onMonthSelect(month: string): void {
    this.selectedMonth.set(month);
    this.selectedTimeRange.set('MONTH');
    this.loadLiveData();
  }

  onCustomDateApply(): void {
    this.selectedTimeRange.set('CUSTOM');
    this.loadLiveData();
  }

  loadLiveData(): void {
    const range = this.selectedTimeRange();
    let queryParams: any = { range };

    if (range === 'MONTH') {
      queryParams.month = this.selectedMonth();
      queryParams.granularity = 'DAILY';
    } else if (range === 'CUSTOM') {
      queryParams.startDate = this.startDate();
      queryParams.endDate = this.endDate();
      queryParams.granularity = this.selectedGranularity();
    }

    this.apiService.getSummary(queryParams).subscribe({
      next: data => { if (data) this.summary.set(data); },
      error: () => {}
    });

    this.apiService.getTraffic(queryParams).subscribe({
      next: data => {
        if (data && data.length > 0) {
          this.trafficData.set(data);
          this.activePoint.set(data[data.length - 1]);
        } else {
          this.trafficData.set([]);
          this.activePoint.set(null);
        }
      },
      error: () => {}
    });

    this.apiService.getCategoriesBreakdown().subscribe({
      next: data => { if (data && data.length > 0) this.categoryData.set(data); },
      error: () => {}
    });

    this.apiService.getTopBlocked().subscribe({
      next: data => { if (data && data.length > 0) this.topBlockedData.set(data); },
      error: () => {}
    });

    this.apiService.getTopClients().subscribe({
      next: data => { if (data && data.length > 0) this.topClientsData.set(data); },
      error: () => {}
    });
  }
}

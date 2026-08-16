import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, SummaryStats, TrafficPoint, CategoryBreakdown, TopBlockedDomain, TopClient } from '../../services/api.service';

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

  // Hover/active tooltip bar for mobile touch interaction
  activePoint = signal<TrafficPoint | null>(null);

  private refreshInterval: any;

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

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadLiveData();
    this.refreshInterval = setInterval(() => {
      // Auto-poll only on real-time modes (1H or 24H)
      if (this.selectedTimeRange() === '1H' || this.selectedTimeRange() === '24H') {
        this.loadLiveData();
      }
    }, 5000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
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

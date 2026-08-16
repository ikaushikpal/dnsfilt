import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService, SummaryStats, DnsLog } from '../../services/api.service';

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './analytics-dashboard.component.html',
  styleUrl: './analytics-dashboard.component.css'
})
export class AnalyticsDashboardComponent implements OnInit, OnDestroy {
  stats = signal<SummaryStats>({ totalQueries: 0, blockedQueries: 0, blockRatePercent: 0, topBlocked: [] });
  logs = signal<DnsLog[]>([]);
  private intervalId: any;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadData();
    this.intervalId = setInterval(() => this.loadData(), 3000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) clearInterval(this.intervalId);
  }

  loadData(): void {
    this.apiService.getSummary().subscribe({ next: s => this.stats.set(s), error: () => {} });
    this.apiService.getLogs().subscribe({ next: l => this.logs.set(l), error: () => {} });
  }
}

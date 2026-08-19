import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, TopBlockedDomain, CategoryBreakdown, SummaryStats } from '../../services/api.service';
import { RefreshService } from '../../services/refresh.service';

@Component({
  selector: 'app-threats',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './threats.component.html',
  styleUrl: './threats.component.css'
})
export class ThreatsComponent implements OnInit, OnDestroy {
  summary = signal<SummaryStats>({
    totalQueries: 0,
    blockedQueries: 0,
    blockRatePercent: 0,
    cacheHitPercent: 0,
    avgLatencyMs: 0,
    activeClients: 0
  });

  threatsData = signal<TopBlockedDomain[]>([]);
  categoriesData = signal<CategoryBreakdown[]>([]);
  private refreshInterval: any;

  // Search & Pagination
  searchTerm = signal<string>('');
  currentPage = signal<number>(1);
  pageSize = signal<number>(10);

  filteredThreats = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const all = this.threatsData();
    if (!term) return all;
    return all.filter(t => 
      t.domain.toLowerCase().includes(term) ||
      t.category.toLowerCase().includes(term)
    );
  });

  totalPages = computed(() => {
    return Math.max(1, Math.ceil(this.filteredThreats().length / this.pageSize()));
  });

  paginatedThreats = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.filteredThreats().slice(start, start + this.pageSize());
  });

  malwareCount = computed(() => {
    const m = this.categoriesData().find(c => c.category.toUpperCase() === 'MALWARE');
    return m ? m.blockedQueries : 0;
  });

  phishingCount = computed(() => {
    const p = this.categoriesData().find(c => c.category.toUpperCase() === 'PHISHING');
    return p ? p.blockedQueries : 0;
  });

  adsCount = computed(() => {
    const a = this.categoriesData().find(c => c.category.toUpperCase() === 'ADS');
    return a ? a.blockedQueries : 0;
  });

  constructor(
    private apiService: ApiService,
    private refreshService: RefreshService
  ) {}

  ngOnInit(): void {
    this.loadData();
    this.refreshInterval = setInterval(() => this.loadData(), 4000);
    this.refreshService.refresh$.subscribe(() => {
      this.loadData();
    });
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

    this.apiService.getTopBlocked().subscribe({
      next: d => { if (d) this.threatsData.set(d); },
      error: () => {}
    });

    this.apiService.getCategoriesBreakdown().subscribe({
      next: c => { if (c) this.categoriesData.set(c); },
      error: () => {}
    });
  }

  onPageChange(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
    }
  }

  onPageSizeChange(size: any): void {
    this.pageSize.set(Number(size));
    this.currentPage.set(1);
  }
}

import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, DomainRule, BlockCategory } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { UserManagementComponent } from '../user-management/user-management.component';

@Component({
  selector: 'app-admin-rules',
  standalone: true,
  imports: [CommonModule, FormsModule, UserManagementComponent],
  templateUrl: './admin-rules.component.html',
  styleUrl: './admin-rules.component.css'
})
export class AdminRulesComponent implements OnInit {
  rules = signal<DomainRule[]>([]);
  categories = signal<BlockCategory[]>([]);
  userRole = signal<string | null>(null);

  // Search & Pagination Signals
  searchTerm = signal<string>('');
  currentPage = signal<number>(1);
  pageSize = signal<number>(10);

  filteredRules = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const all = this.rules();
    if (!term) return all;
    return all.filter(r => 
      r.domain.toLowerCase().includes(term) ||
      r.category.toLowerCase().includes(term) ||
      (r.reason && r.reason.toLowerCase().includes(term))
    );
  });

  totalPages = computed(() => {
    return Math.max(1, Math.ceil(this.filteredRules().length / this.pageSize()));
  });

  paginatedRules = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.filteredRules().slice(start, start + this.pageSize());
  });

  newDomain = signal('');
  newAction = signal<'BLOCK' | 'ALLOW'>('BLOCK');
  newMatchType = signal<'EXACT' | 'DOMAIN_AND_SUBDOMAINS'>('DOMAIN_AND_SUBDOMAINS');
  newCategory = signal('MALWARE');
  newSeverity = signal('CRITICAL');
  newReason = signal('Security policy rule');

  constructor(private apiService: ApiService, private authService: AuthService) {}

  ngOnInit(): void {
    this.userRole.set(this.authService.getRole() || 'ROLE_ADMIN');
    this.loadData();
  }

  loadData(): void {
    this.apiService.getCategories().subscribe({
      next: c => { if (c) this.categories.set(c); },
      error: () => {}
    });

    this.apiService.getRules().subscribe({
      next: r => { if (r) this.rules.set(r); },
      error: () => {}
    });
  }

  onAdd(): void {
    const domain = this.newDomain().trim();
    if (!domain) return;

    const newRule: DomainRule = {
      id: Date.now(),
      domain,
      action: this.newAction(),
      matchType: this.newMatchType(),
      category: this.newCategory(),
      severity: this.newSeverity(),
      reason: this.newReason().trim()
    };

    this.rules.set([newRule, ...this.rules().filter(r => r.domain !== domain)]);

    this.apiService.addRule(newRule).subscribe({
      next: () => this.loadData(),
      error: () => {}
    });

    this.newDomain.set('');
  }

  onDelete(domain: string): void {
    this.rules.set(this.rules().filter(r => r.domain !== domain));

    this.apiService.deleteRule(domain).subscribe({
      next: () => this.loadData(),
      error: () => {}
    });
  }

  syncing = signal<boolean>(false);
  syncMessage = signal<string | null>(null);

  onSyncRules(): void {
    this.syncing.set(true);
    this.syncMessage.set(null);
    this.apiService.syncRules().subscribe({
      next: (res) => {
        this.syncing.set(false);
        this.syncMessage.set(res?.message || 'Rules flushed to Redis & blocklist version incremented.');
        setTimeout(() => this.syncMessage.set(null), 4000);
      },
      error: (err) => {
        this.syncing.set(false);
        this.syncMessage.set(err?.error?.message || 'Sync failed. Check Redis connectivity.');
        setTimeout(() => this.syncMessage.set(null), 4000);
      }
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

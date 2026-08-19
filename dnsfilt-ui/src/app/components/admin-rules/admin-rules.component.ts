import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, DomainRule, BlockCategory } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { RefreshService } from '../../services/refresh.service';
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

  constructor(
    private apiService: ApiService, 
    private authService: AuthService,
    private refreshService: RefreshService
  ) {}

  ngOnInit(): void {
    this.userRole.set(this.authService.getRole() || 'ROLE_ADMIN');
    this.loadData();
    this.refreshService.refresh$.subscribe(() => {
      this.loadData();
    });
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

    const isAllow = this.newAction() === 'ALLOW';
    const newRule: DomainRule = {
      id: Date.now(),
      domain,
      action: this.newAction(),
      matchType: this.newMatchType(),
      category: isAllow ? 'WHITELIST' : (this.newCategory() || 'GENERAL'),
      severity: isAllow ? 'LOW' : this.newSeverity(),
      reason: isAllow ? 'Domain whitelist bypass' : this.newReason().trim()
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

  // --- CSV Export & Batch Import Methods ---
  showImportModal = signal<boolean>(false);
  showHelpModal = signal<boolean>(false);
  importing = signal<boolean>(false);
  importFileName = signal<string>('');
  parsedRules = signal<Partial<DomainRule>[]>([]);
  importErrors = signal<string[]>([]);
  importSuccessMessage = signal<string | null>(null);

  downloadSampleCsv(): void {
    const sampleContent = 
`Domain,Action,MatchType,Category,Severity,Reason
malware-payload.ru,BLOCK,DOMAIN_AND_SUBDOMAINS,MALWARE,CRITICAL,Ransomware C2 server
trackers.adtech.net,BLOCK,DOMAIN_AND_SUBDOMAINS,TRACKING,HIGH,User tracking telemetry
cdn.trustedpartner.com,ALLOW,DOMAIN_AND_SUBDOMAINS,WHITELIST,LOW,Trusted partner CDN bypass
promo.annoyingads.com,BLOCK,EXACT,ADS,MEDIUM,Exact promotional ad banner
phish-banking-login.cc,BLOCK,DOMAIN_AND_SUBDOMAINS,PHISHING,CRITICAL,Banking credential harvester`;

    const blob = new Blob([sampleContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', 'dnsfilt_rules_sample.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  exportToCsv(): void {
    const allRules = this.rules();
    if (!allRules || allRules.length === 0) return;

    let csvContent = 'Domain,Action,MatchType,Category,Severity,Reason\n';
    for (const r of allRules) {
      const domain = `"${(r.domain || '').replace(/"/g, '""')}"`;
      const action = `"${(r.action || 'BLOCK').replace(/"/g, '""')}"`;
      const matchType = `"${(r.matchType || 'DOMAIN_AND_SUBDOMAINS').replace(/"/g, '""')}"`;
      const category = `"${(r.category || 'OTHER').replace(/"/g, '""')}"`;
      const severity = `"${(r.severity || 'MEDIUM').replace(/"/g, '""')}"`;
      const reason = `"${(r.reason || '').replace(/"/g, '""')}"`;
      csvContent += `${domain},${action},${matchType},${category},${severity},${reason}\n`;
    }

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `dnsfilt_rules_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  onCsvFileSelected(event: any): void {
    const file = event.target.files?.[0];
    if (!file) return;

    this.importFileName.set(file.name);
    this.importErrors.set([]);
    this.importSuccessMessage.set(null);

    const reader = new FileReader();
    reader.onload = (e: any) => {
      const text = e.target.result as string;
      this.parseCsvContent(text);
      event.target.value = ''; // Reset input
    };
    reader.readAsText(file);
  }

  private parseCsvContent(csvText: string): void {
    const lines = csvText.split(/\r\n|\n/).map(l => l.trim()).filter(l => l.length > 0);
    if (lines.length === 0) {
      this.importErrors.set(['The selected CSV file is empty.']);
      return;
    }

    const parsed: Partial<DomainRule>[] = [];

    // Check if line 0 is a header
    let startIdx = 0;
    const firstLineLower = lines[0].toLowerCase();
    if (firstLineLower.includes('domain') || firstLineLower.includes('action') || firstLineLower.includes('category')) {
      startIdx = 1;
    }

    for (let i = startIdx; i < lines.length; i++) {
      const line = lines[i];
      const fields = this.parseCsvLine(line);
      if (!fields || fields.length === 0 || !fields[0]) continue;

      const rawDomain = fields[0].trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
      if (!rawDomain) continue;

      // Extract Action
      let action: 'BLOCK' | 'ALLOW' = 'BLOCK';
      if (fields[1] && fields[1].trim().toUpperCase() === 'ALLOW') {
        action = 'ALLOW';
      }

      // Extract MatchType
      let matchType: 'EXACT' | 'DOMAIN_AND_SUBDOMAINS' = 'DOMAIN_AND_SUBDOMAINS';
      if (fields[2] && fields[2].trim().toUpperCase() === 'EXACT') {
        matchType = 'EXACT';
      }

      // Extract Category
      let category = 'MALWARE';
      if (action === 'ALLOW') {
        category = 'WHITELIST';
      } else if (fields[3] && fields[3].trim()) {
        category = fields[3].trim().toUpperCase();
      }

      // Extract Severity
      let severity = action === 'ALLOW' ? 'LOW' : 'HIGH';
      if (fields[4] && fields[4].trim()) {
        severity = fields[4].trim().toUpperCase();
      }

      // Extract Reason
      const reason = fields[5] ? fields[5].trim() : (action === 'ALLOW' ? 'Whitelist import' : 'CSV bulk import rule');

      parsed.push({
        domain: rawDomain,
        action,
        matchType,
        category,
        severity,
        reason
      });
    }

    if (parsed.length === 0) {
      this.importErrors.set(['No valid domain entries could be parsed from the CSV file.']);
      return;
    }

    this.parsedRules.set(parsed);
    this.openImportModal();
  }

  private parseCsvLine(text: string): string[] {
    const result: string[] = [];
    let cur = '';
    let inQuotes = false;

    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (c === '"') {
        inQuotes = !inQuotes;
      } else if ((c === ',' || c === ';') && !inQuotes) {
        result.push(cur.trim().replace(/^"|"$/g, ''));
        cur = '';
      } else {
        cur += c;
      }
    }
    result.push(cur.trim().replace(/^"|"$/g, ''));
    return result;
  }

  removeParsedRule(index: number): void {
    this.parsedRules.update(list => list.filter((_, i) => i !== index));
  }

  confirmBatchImport(): void {
    const list = this.parsedRules();
    if (!list || list.length === 0) return;

    this.importing.set(true);
    this.apiService.batchAddRules(list).subscribe({
      next: (res) => {
        this.importing.set(false);
        this.closeImportModal();
        this.syncMessage.set(res?.message || `Successfully imported ${list.length} rules.`);
        this.loadData();
        setTimeout(() => this.syncMessage.set(null), 4000);
      },
      error: (err) => {
        this.importing.set(false);
        this.importErrors.set([err?.error?.message || 'Failed to import rules batch. Please try again.']);
      }
    });
  }

  openHelpModal(): void {
    this.showHelpModal.set(true);
    if (typeof document !== 'undefined') {
      document.body.style.overflow = 'hidden';
    }
  }

  closeHelpModal(): void {
    this.showHelpModal.set(false);
    if (typeof document !== 'undefined') {
      document.body.style.overflow = '';
    }
  }

  openImportModal(): void {
    this.showImportModal.set(true);
    if (typeof document !== 'undefined') {
      document.body.style.overflow = 'hidden';
    }
  }

  closeImportModal(): void {
    this.showImportModal.set(false);
    this.parsedRules.set([]);
    this.importErrors.set([]);
    if (typeof document !== 'undefined') {
      document.body.style.overflow = '';
    }
  }

  ngOnDestroy(): void {
    if (typeof document !== 'undefined') {
      document.body.style.overflow = '';
    }
  }
}

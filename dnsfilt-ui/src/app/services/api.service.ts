import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface SummaryStats {
  totalQueries: number;
  blockedQueries: number;
  blockRatePercent: number;
  cacheHitPercent: number;
  avgLatencyMs: number;
  activeClients: number;
}

export interface TrafficPoint {
  time: string;
  totalQueries: number;
  blockedQueries: number;
}

export interface CategoryBreakdown {
  category: string;
  totalQueries: number;
  blockedQueries: number;
}

export interface TopBlockedDomain {
  rank: number;
  domain: string;
  category: string;
  requests: number;
  blockedRequests: number;
  clients: number;
}

export interface TopClient {
  clientHash: string;
  totalQueries: number;
  blockedQueries: number;
  blockRate: number;
  distinctDomains: number;
  riskLevel: string;
  riskBadge: string;
}

export interface DomainRule {
  id?: number;
  domain: string;
  action: 'BLOCK' | 'ALLOW';
  matchType: 'EXACT' | 'DOMAIN_AND_SUBDOMAINS';
  category: string;
  severity: string;
  status?: string;
  reason?: string;
}

export interface BlockCategory {
  id: number;
  name: string;
  description: string;
}

export interface DomainRecord {
  id?: number;
  domain: string;
  recordType: string;
  ipAddress: string;
  ttl?: number;
  description?: string;
}

export interface ResolverConfig {
  id?: number;
  desiredCount: number;
  desiredVersion: string;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private apiBase: string;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (isPlatformBrowser(this.platformId)) {
      if (window.location.port === '4200') {
        const customPort = localStorage.getItem('dnsfilt_backend_port') || '9090';
        this.apiBase = `http://${window.location.hostname}:${customPort}/api`;
      } else {
        this.apiBase = `${window.location.origin}/api`;
      }
    } else {
      this.apiBase = 'http://localhost:9090/api';
    }
  }

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    });
  }

  // --- Real-time & Historical Analytics Endpoints ---
  getSummary(params?: { range?: string; month?: string; startDate?: string; endDate?: string }): Observable<SummaryStats> {
    const queryParams: any = {};
    if (params?.range) queryParams.range = params.range;
    if (params?.month) queryParams.month = params.month;
    if (params?.startDate) queryParams.startDate = params.startDate;
    if (params?.endDate) queryParams.endDate = params.endDate;

    return this.http.get<SummaryStats>(`${this.apiBase}/v1/analytics/summary`, {
      headers: this.getHeaders(),
      params: queryParams
    });
  }

  getTraffic(params?: { range?: string; month?: string; startDate?: string; endDate?: string; granularity?: string }): Observable<TrafficPoint[]> {
    const queryParams: any = {};
    if (params?.range) queryParams.range = params.range;
    if (params?.month) queryParams.month = params.month;
    if (params?.startDate) queryParams.startDate = params.startDate;
    if (params?.endDate) queryParams.endDate = params.endDate;
    if (params?.granularity) queryParams.granularity = params.granularity;

    return this.http.get<TrafficPoint[]>(`${this.apiBase}/v1/analytics/traffic`, {
      headers: this.getHeaders(),
      params: queryParams
    });
  }

  getCategoriesBreakdown(): Observable<CategoryBreakdown[]> {
    return this.http.get<CategoryBreakdown[]>(`${this.apiBase}/v1/analytics/categories`, { headers: this.getHeaders() });
  }

  getTopBlocked(): Observable<TopBlockedDomain[]> {
    return this.http.get<TopBlockedDomain[]>(`${this.apiBase}/v1/analytics/top-blocked`, { headers: this.getHeaders() });
  }

  getTopClients(): Observable<TopClient[]> {
    return this.http.get<TopClient[]>(`${this.apiBase}/v1/analytics/top-clients`, { headers: this.getHeaders() });
  }

  // --- Domain Rules & Categories ---
  getCategories(): Observable<BlockCategory[]> {
    return this.http.get<BlockCategory[]>(`${this.apiBase}/rules/categories`, { headers: this.getHeaders() });
  }

  getRules(): Observable<DomainRule[]> {
    return this.http.get<DomainRule[]>(`${this.apiBase}/rules`, { headers: this.getHeaders() });
  }

  addRule(rule: Partial<DomainRule>): Observable<DomainRule> {
    return this.http.post<DomainRule>(`${this.apiBase}/rules`, rule, { headers: this.getHeaders() });
  }

  batchAddRules(rules: Partial<DomainRule>[]): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiBase}/rules/batch`, rules, { headers: this.getHeaders() });
  }

  syncRules(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiBase}/rules/sync`, {}, { headers: this.getHeaders() });
  }

  deleteRule(domain: string): Observable<any> {
    return this.http.delete<any>(`${this.apiBase}/rules/${domain}`, { headers: this.getHeaders() });
  }

  // --- Custom Domain Records ---
  getDomains(): Observable<DomainRecord[]> {
    return this.http.get<DomainRecord[]>(`${this.apiBase}/v1/domains`, { headers: this.getHeaders() });
  }

  createDomain(domainRecord: Partial<DomainRecord>): Observable<DomainRecord> {
    return this.http.post<DomainRecord>(`${this.apiBase}/v1/domains`, domainRecord, { headers: this.getHeaders() });
  }

  deleteDomain(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/v1/domains/${id}`, { headers: this.getHeaders() });
  }

  // --- Resolver Scaling & Version Config ---
  getResolverConfig(): Observable<ResolverConfig> {
    return this.http.get<ResolverConfig>(`${this.apiBase}/v1/resolver/config`, { headers: this.getHeaders() });
  }

  updateResolverCount(count: number): Observable<ResolverConfig> {
    return this.http.put<ResolverConfig>(`${this.apiBase}/v1/resolver/count`, { count }, { headers: this.getHeaders() });
  }

  updateResolverVersion(version: string): Observable<ResolverConfig> {
    return this.http.put<ResolverConfig>(`${this.apiBase}/v1/resolver/version`, { version }, { headers: this.getHeaders() });
  }

  // --- User Administration ---
  getUsers(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.apiBase}/users`, { headers: this.getHeaders() });
  }

  createUser(user: { username: string; password: string; email?: string; role: string }): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.apiBase}/users`, user, { headers: this.getHeaders() });
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/users/${id}`, { headers: this.getHeaders() });
  }
}

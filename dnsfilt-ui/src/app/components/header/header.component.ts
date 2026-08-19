import { Component, EventEmitter, Input, Output, OnInit, OnDestroy, signal, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { AuthService } from '../../services/auth.service';

export type NavTab = 'home' | 'guide' | 'dashboard' | 'clients' | 'threats' | 'resolvers' | 'toys' | 'learn' | 'about' | 'contact' | 'admin' | 'users';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.component.html',
  styles: []
})
export class HeaderComponent implements OnInit, OnDestroy {
  activeTab = signal<NavTab>('home');
  currentUser = signal<string | null>(null);
  role = signal<string | null>(null);
  gmtTime = signal<string>('');

  private timer: any;

  constructor(
    public authService: AuthService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  @Input('activeTab') set setActiveTab(val: NavTab) {
    this.activeTab.set(val);
  }

  @Input('currentUser') set setCurrentUser(val: string | null) {
    this.currentUser.set(val);
  }

  @Input('role') set setRole(val: string | null) {
    this.role.set(val);
  }

  @Output() tabChange = new EventEmitter<NavTab>();
  @Output() openLogin = new EventEmitter<void>();
  @Output() toggleSidebar = new EventEmitter<void>();

  ngOnInit(): void {
    this.updateTime();
    if (isPlatformBrowser(this.platformId)) {
      this.timer = setInterval(() => this.updateTime(), 1000);
    }
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  private updateTime(): void {
    const now = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    const yyyy = now.getUTCFullYear();
    const mm = pad(now.getUTCMonth() + 1);
    const dd = pad(now.getUTCDate());
    const hh = pad(now.getUTCHours());
    const min = pad(now.getUTCMinutes());
    const ss = pad(now.getUTCSeconds());
    this.gmtTime.set(`${yyyy}-${mm}-${dd} ${hh}:${min}:${ss} UTC`);
  }

  hasAuthToken(): boolean {
    if (isPlatformBrowser(this.platformId)) {
      return !!localStorage.getItem('accessToken');
    }
    return !!this.currentUser();
  }

  authButtonLabel(): string {
    return this.hasAuthToken() ? 'Dashboard' : 'Login';
  }

  authButtonIcon(): string {
    return this.hasAuthToken() ? 'fa-solid fa-chart-pie text-xs' : 'fa-solid fa-right-to-bracket text-xs';
  }

  onAuthAction(): void {
    if (this.hasAuthToken()) {
      this.selectTab('dashboard');
    } else {
      this.openLogin.emit();
    }
  }

  selectTab(tab: NavTab): void {
    this.activeTab.set(tab);
    this.tabChange.emit(tab);
  }

  isDashboardTab(): boolean {
    const t = this.activeTab();
    return t === 'dashboard' || t === 'clients' || t === 'threats' || t === 'resolvers' || t === 'admin' || t === 'users';
  }
}

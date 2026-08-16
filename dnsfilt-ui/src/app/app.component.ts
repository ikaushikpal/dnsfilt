import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './services/auth.service';

// Modular Child Components
import { HeaderComponent, NavTab } from './components/header/header.component';
import { ToastComponent } from './components/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    HeaderComponent,
    ToastComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  title = 'dnsfilt-ui';

  // Active Navigation tab
  activeTab: NavTab = 'home';

  // Toast notification state
  toastMessage: string | null = null;
  private toastTimer: any;

  // Auth state
  currentUser: string | null = null;
  userRole: string | null = null;

  constructor(
    public authService: AuthService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    // Sync current active tab with router URL changes
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const url = event.urlAfterRedirects || event.url;
      if (url.includes('/dashboard/clients') || url === '/clients') this.activeTab = 'clients';
      else if (url.includes('/dashboard/threats') || url === '/threats') this.activeTab = 'threats';
      else if (url.includes('/dashboard/resolvers') || url === '/resolvers') this.activeTab = 'resolvers';
      else if (url.includes('/dashboard/rules') || url.includes('/dashboard/admin') || url === '/admin') this.activeTab = 'admin';
      else if (url.includes('/dashboard')) this.activeTab = 'dashboard';
      else if (url.includes('/home')) this.activeTab = 'home';
      else if (url.includes('/learn')) this.activeTab = 'learn';
      else if (url.includes('/contact')) this.activeTab = 'contact';
      else if (url.includes('/services')) this.activeTab = 'toys';
    });

    // Subscribe to auth state
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.userRole = this.authService.getRole();
    });
  }

  onTabChange(tab: NavTab): void {
    if (tab === 'home') this.router.navigate(['/home']);
    else if (tab === 'dashboard') this.router.navigate(['/dashboard']);
    else if (tab === 'clients') this.router.navigate(['/dashboard/clients']);
    else if (tab === 'threats') this.router.navigate(['/dashboard/threats']);
    else if (tab === 'resolvers') this.router.navigate(['/dashboard/resolvers']);
    else if (tab === 'toys') this.router.navigate(['/services']);
    else if (tab === 'learn') this.router.navigate(['/learn']);
    else if (tab === 'contact') this.router.navigate(['/contact']);
    else if (tab === 'admin') {
      if (!this.currentUser) {
        this.router.navigate(['/login']);
        this.showToast('Please sign in to access Security Rules Admin');
      } else {
        this.router.navigate(['/dashboard/rules']);
      }
    }
  }

  navigateToLogin(): void {
    this.router.navigate(['/login']);
  }

  showToast(msg: string): void {
    this.toastMessage = msg;
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastMessage = null, 2500);
  }

  onLogout(): void {
    this.authService.logout();
    this.showToast('Logged out');
    this.router.navigate(['/home']);
  }
}

import { Component, OnInit, Inject, PLATFORM_ID, signal, HostListener } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './services/auth.service';

import { RefreshService } from './services/refresh.service';

// Modular Child Components
import { HeaderComponent, NavTab } from './components/header/header.component';
import { ToastComponent } from './components/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterOutlet,
    HeaderComponent,
    ToastComponent
  ],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent implements OnInit {
  title = 'dnsfilt-ui';

  // Active Navigation tab
  activeTab: NavTab = 'home';
  sidebarOpen: boolean = false;

  // Toast notification state
  toastMessage: string | null = null;
  private toastTimer: any;

  // Auth state
  currentUser: string | null = null;
  userRole: string | null = null;

  // Floating Refresh state
  isRefreshing = signal(false);

  // Smart Navbar Scroll Visibility (Past 100vh scroll-up reveal)
  isNavbarVisible = signal(true);
  private lastScrollY = 0;
  private scrollThreshold = 8;

  // Change Password Modal State
  showChangePasswordModal = signal(false);
  oldPassword = signal('');
  newPassword = signal('');
  showOldPassword = signal(false);
  showNewPassword = signal(false);
  changePasswordLoading = signal(false);
  changePasswordError = signal<string | null>(null);
  changePasswordSuccess = signal<string | null>(null);

  constructor(
    public authService: AuthService,
    private refreshService: RefreshService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    // Inside dashboard, keep navbar fixed at top
    if (this.isDashboardRoute()) {
      this.isNavbarVisible.set(true);
      return;
    }

    const currentScrollY = window.scrollY || document.documentElement.scrollTop || 0;
    const vh100 = window.innerHeight || 700;

    if (currentScrollY <= vh100) {
      // First 100vh: always show
      this.isNavbarVisible.set(true);
    } else {
      // Past 100vh: scroll down hides, scroll up reveals
      const delta = currentScrollY - this.lastScrollY;
      if (delta > this.scrollThreshold) {
        this.isNavbarVisible.set(false);
      } else if (delta < -this.scrollThreshold) {
        this.isNavbarVisible.set(true);
      }
    }

    this.lastScrollY = currentScrollY;
  }

  triggerGlobalRefresh(): void {
    this.isRefreshing.set(true);
    this.refreshService.triggerRefresh();
    this.showToast('Refreshing dashboard data...');
    setTimeout(() => this.isRefreshing.set(false), 700);
  }

  ngOnInit(): void {
    // If desktop, default sidebar to open
    if (isPlatformBrowser(this.platformId) && window.innerWidth >= 1024) {
      this.sidebarOpen = true;
    }

    // Sync current active tab with router URL changes
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const url = event.urlAfterRedirects || event.url;
      if (url.includes('/dashboard/clients') || url === '/clients') this.activeTab = 'clients';
      else if (url.includes('/dashboard/threats') || url === '/threats') this.activeTab = 'threats';
      else if (url.includes('/dashboard/resolvers') || url === '/resolvers') this.activeTab = 'resolvers';
      else if (url.includes('/dashboard/rules') || url.includes('/dashboard/admin') || url === '/admin') this.activeTab = 'admin';
      else if (url.includes('/dashboard/users') || url === '/users') this.activeTab = 'users';
      else if (url.includes('/dashboard')) this.activeTab = 'dashboard';
      else if (url.includes('/guide')) this.activeTab = 'guide';
      else if (url.includes('/learn')) this.activeTab = 'learn';
      else if (url.includes('/about')) this.activeTab = 'about';
      else if (url.includes('/contact')) this.activeTab = 'contact';
      else if (url.includes('/services')) this.activeTab = 'toys';
      else if (url.includes('/home') || url === '/') this.activeTab = 'home';
    });

    // Subscribe to auth state
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.userRole = this.authService.getRole();
    });

    // Validate session with backend on load / reload
    if (isPlatformBrowser(this.platformId) && this.authService.isLoggedIn()) {
      this.authService.validateSession().subscribe({
        next: (res) => {
          if (!res || !res.valid) {
            if (this.isDashboardRoute()) {
              this.router.navigate(['/login']);
            }
          }
        },
        error: () => {
          if (this.isDashboardRoute()) {
            this.router.navigate(['/login']);
          }
        }
      });
    }
  }

  isDashboardRoute(): boolean {
    return this.activeTab === 'dashboard' || 
           this.activeTab === 'clients' || 
           this.activeTab === 'threats' || 
           this.activeTab === 'resolvers' || 
           this.activeTab === 'admin' || 
           this.activeTab === 'users';
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  closeSidebar(): void {
    if (isPlatformBrowser(this.platformId) && window.innerWidth < 1024) {
      this.sidebarOpen = false;
    }
  }

  onTabChange(tab: NavTab): void {
    this.closeSidebar();
    if (tab === 'home') this.router.navigate(['/home']);
    else if (tab === 'guide') this.router.navigate(['/guide']);
    else if (tab === 'dashboard') this.router.navigate(['/dashboard']);
    else if (tab === 'clients') this.router.navigate(['/dashboard/clients']);
    else if (tab === 'threats') this.router.navigate(['/dashboard/threats']);
    else if (tab === 'resolvers') this.router.navigate(['/dashboard/resolvers']);
    else if (tab === 'toys') this.router.navigate(['/services']);
    else if (tab === 'learn') this.router.navigate(['/learn']);
    else if (tab === 'about') {
      if (this.router.url.includes('/home') || this.router.url === '/') {
        const el = document.getElementById('about-author-section');
        if (el) {
          el.scrollIntoView({ behavior: 'smooth' });
        }
      } else {
        this.router.navigate(['/home']).then(() => {
          setTimeout(() => {
            const el = document.getElementById('about-author-section');
            if (el) {
              el.scrollIntoView({ behavior: 'smooth' });
            }
          }, 150);
        });
      }
    }
    else if (tab === 'contact') this.router.navigate(['/contact']);
    else if (tab === 'users') this.router.navigate(['/dashboard/users']);
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

  openChangePasswordModal(): void {
    this.oldPassword.set('');
    this.newPassword.set('');
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(null);
    this.showChangePasswordModal.set(true);
  }

  closeChangePasswordModal(): void {
    this.showChangePasswordModal.set(false);
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(null);
  }

  submitChangePassword(): void {
    const oldP = this.oldPassword().trim();
    const newP = this.newPassword().trim();

    if (!oldP || !newP) {
      this.changePasswordError.set('Please enter both current and new passwords.');
      return;
    }

    if (newP.length < 4) {
      this.changePasswordError.set('New password must be at least 4 characters.');
      return;
    }

    this.changePasswordLoading.set(true);
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(null);

    this.authService.changePassword({ oldPassword: oldP, newPassword: newP }).subscribe({
      next: () => {
        this.changePasswordLoading.set(false);
        this.changePasswordSuccess.set('Password updated successfully!');
        setTimeout(() => {
          this.closeChangePasswordModal();
          this.showToast('Password changed successfully');
        }, 1500);
      },
      error: (err) => {
        this.changePasswordLoading.set(false);
        this.changePasswordError.set(err?.error?.error || 'Failed to update password. Please check your current password.');
      }
    });
  }
}

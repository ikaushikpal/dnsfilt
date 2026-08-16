import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

export type NavTab = 'home' | 'dashboard' | 'clients' | 'threats' | 'resolvers' | 'toys' | 'learn' | 'contact' | 'admin';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent {
  activeTab = signal<NavTab>('home');
  currentUser = signal<string | null>(null);
  role = signal<string | null>(null);

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
  @Output() onLogout = new EventEmitter<void>();

  selectTab(tab: NavTab): void {
    this.activeTab.set(tab);
    this.tabChange.emit(tab);
  }

  isDashboardTab(): boolean {
    const t = this.activeTab();
    return t === 'dashboard' || t === 'clients' || t === 'threats' || t === 'resolvers' || t === 'admin';
  }
}

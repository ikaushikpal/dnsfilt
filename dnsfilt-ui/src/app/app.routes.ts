import { Routes } from '@angular/router';
import { authGuard, adminRoleGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full'
  },
  {
    path: 'home',
    loadComponent: () => import('./components/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'services',
    loadComponent: () => import('./components/cli-services/cli-services.component').then(m => m.CliServicesComponent)
  },
  {
    path: 'learn',
    loadComponent: () => import('./components/learn/learn.component').then(m => m.LearnComponent)
  },
  {
    path: 'contact',
    loadComponent: () => import('./components/contact/contact.component').then(m => m.ContactComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent)
  },

  // Dashboard Base & Sub-pages
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'dashboard/clients',
    loadComponent: () => import('./components/clients/clients.component').then(m => m.ClientsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'dashboard/threats',
    loadComponent: () => import('./components/threats/threats.component').then(m => m.ThreatsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'dashboard/resolvers',
    loadComponent: () => import('./components/resolvers/resolvers.component').then(m => m.ResolversComponent),
    canActivate: [authGuard]
  },
  {
    path: 'dashboard/rules',
    loadComponent: () => import('./components/admin-rules/admin-rules.component').then(m => m.AdminRulesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'dashboard/admin',
    redirectTo: 'dashboard/rules',
    pathMatch: 'full'
  },
  {
    path: 'dashboard/users',
    loadComponent: () => import('./components/user-management/user-management.component').then(m => m.UserManagementComponent),
    canActivate: [authGuard, adminRoleGuard]
  },

  // Direct Shortcuts / Aliases
  {
    path: 'clients',
    redirectTo: 'dashboard/clients',
    pathMatch: 'full'
  },
  {
    path: 'threats',
    redirectTo: 'dashboard/threats',
    pathMatch: 'full'
  },
  {
    path: 'resolvers',
    redirectTo: 'dashboard/resolvers',
    pathMatch: 'full'
  },
  {
    path: 'admin',
    redirectTo: 'dashboard/rules',
    pathMatch: 'full'
  },
  {
    path: 'users',
    redirectTo: 'dashboard/users',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: 'home'
  }
];

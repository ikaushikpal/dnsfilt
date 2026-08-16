import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  // Not authenticated — safely redirect to /login and preserve destination
  router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
  return false;
};

export const adminRoleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  if (authService.isLoggedIn() && authService.getRole() === 'ROLE_ADMIN') {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, throwError, switchMap } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // 401 Unauthorized or 403 Forbidden indicates token expiration or invalid session
      if (error.status === 401 || error.status === 403) {
        // Avoid infinite loop on auth endpoints
        const isAuthReq = req.url.includes('/api/auth/login') ||
                          req.url.includes('/api/auth/refresh') ||
                          req.url.includes('/api/auth/validate');

        if (!isAuthReq && authService.getRefreshToken()) {
          return authService.refreshToken().pipe(
            switchMap((res: any) => {
              if (res && res.accessToken) {
                const retryReq = req.clone({
                  setHeaders: {
                    Authorization: `Bearer ${res.accessToken}`
                  }
                });
                return next(retryReq);
              }
              authService.logout();
              return throwError(() => error);
            }),
            catchError((refreshErr) => {
              authService.logout();
              return throwError(() => refreshErr);
            })
          );
        } else if (!isAuthReq) {
          // No refresh token or refresh failed — clear state
          authService.logout();
        }
      }
      return throwError(() => error);
    })
  );
};

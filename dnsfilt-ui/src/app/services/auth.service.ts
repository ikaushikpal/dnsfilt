import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  username: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiBase: string;
  private currentUserSubject = new BehaviorSubject<string | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (isPlatformBrowser(this.platformId)) {
      if (window.location.port === '4200') {
        this.apiBase = 'http://localhost:9090/api/auth';
      } else {
        this.apiBase = `${window.location.origin}/api/auth`;
      }

      const token = localStorage.getItem('accessToken');
      const username = localStorage.getItem('username');
      if (token && username) {
        this.currentUserSubject.next(username);
      } else {
        this.currentUserSubject.next(null);
      }
    } else {
      this.apiBase = 'http://localhost:9090/api/auth';
    }
  }

  login(credentials: { username: string; password: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiBase}/login`, credentials).pipe(
      tap(res => {
        if (isPlatformBrowser(this.platformId)) {
          localStorage.setItem('accessToken', res.accessToken);
          localStorage.setItem('refreshToken', res.refreshToken);
          localStorage.setItem('username', res.username);
          localStorage.setItem('role', res.role);
          this.currentUserSubject.next(res.username);
        }
      })
    );
  }

  /**
   * Validates local tokens with the backend.
   * If access token is expired, backend automatically uses refresh token to issue a new access token.
   * If both are invalid/expired, clears session and triggers logout.
   */
  validateSession(): Observable<any> {
    const accessToken = this.getToken();
    const refreshToken = this.getRefreshToken();

    if (!accessToken && !refreshToken) {
      this.currentUserSubject.next(null);
      return new Observable(obs => { obs.next(false); obs.complete(); });
    }

    return this.http.post<any>(`${this.apiBase}/validate`, { accessToken, refreshToken }).pipe(
      tap(res => {
        if (res && res.valid) {
          if (isPlatformBrowser(this.platformId)) {
            if (res.accessToken) localStorage.setItem('accessToken', res.accessToken);
            if (res.refreshToken) localStorage.setItem('refreshToken', res.refreshToken);
            if (res.username) localStorage.setItem('username', res.username);
            if (res.role) localStorage.setItem('role', res.role);
            this.currentUserSubject.next(res.username);
          }
        } else {
          this.logout();
        }
      })
    );
  }

  refreshToken(): Observable<any> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.logout();
      return new Observable(obs => { obs.error(new Error('No refresh token')); });
    }

    return this.http.post<any>(`${this.apiBase}/refresh`, { refreshToken }).pipe(
      tap(res => {
        if (isPlatformBrowser(this.platformId) && res.accessToken) {
          localStorage.setItem('accessToken', res.accessToken);
          if (res.refreshToken) localStorage.setItem('refreshToken', res.refreshToken);
        }
      })
    );
  }

  changePassword(data: { oldPassword: string; newPassword: string }): Observable<any> {
    const token = this.getToken();
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
    return this.http.post(`${this.apiBase}/change-password`, data, { headers });
  }

  logout(): void {
    const refreshToken = this.getRefreshToken();
    const token = this.getToken();

    if (isPlatformBrowser(this.platformId)) {
      if (token) {
        const headers = new HttpHeaders({
          'Authorization': `Bearer ${token}`
        });
        this.http.post(`${this.apiBase}/logout`, { refreshToken }, { headers }).subscribe({
          next: () => {},
          error: () => {}
        });
      }
      localStorage.clear();
      this.currentUserSubject.next(null);
    }
  }

  deleteSelfAccount(): Observable<any> {
    let url = '/api/users/me';
    if (isPlatformBrowser(this.platformId) && window.location.port === '4200') {
      url = 'http://localhost:9090/api/users/me';
    }
    return this.http.delete(url);
  }

  getToken(): string | null {
    if (isPlatformBrowser(this.platformId)) {
      return localStorage.getItem('accessToken');
    }
    return null;
  }

  getRefreshToken(): string | null {
    if (isPlatformBrowser(this.platformId)) {
      return localStorage.getItem('refreshToken');
    }
    return null;
  }

  getRole(): string | null {
    if (isPlatformBrowser(this.platformId)) {
      return localStorage.getItem('role') || 'ROLE_VIEWER';
    }
    return 'ROLE_VIEWER';
  }

  isLoggedIn(): boolean {
    if (isPlatformBrowser(this.platformId)) {
      return !!localStorage.getItem('accessToken');
    }
    return false;
  }
}

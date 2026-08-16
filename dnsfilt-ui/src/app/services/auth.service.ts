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
      // If running through Angular dev server (port 4200), target backend on 9090
      if (window.location.port === '4200') {
        this.apiBase = 'http://localhost:9090/api/auth';
      } else {
        this.apiBase = `${window.location.origin}/api/auth`;
      }

      const username = localStorage.getItem('username');
      if (username) {
        this.currentUserSubject.next(username);
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

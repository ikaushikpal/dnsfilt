import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  username = signal('');
  password = signal('');
  loading = signal(false);
  error = signal<string | null>(null);

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  onSubmit(): void {
    const u = this.username().trim();
    const p = this.password().trim();

    if (!u || !p) {
      this.error.set('Please enter both username and password.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.authService.login({ username: u, password: p }).subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401 || err.status === 403) {
          this.error.set('Invalid username or password.');
        } else {
          this.error.set(err?.error?.message || 'Authentication failed. Please verify credentials and backend status.');
        }
      }
    });
  }
}

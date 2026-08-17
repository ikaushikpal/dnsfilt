import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.css'
})
export class UserManagementComponent implements OnInit {
  users = signal<any[]>([]);
  currentUsername = signal<string>('');
  currentRole = signal<string>('');

  newUserName = signal('');
  newUserPass = signal('');
  newUserRole = signal('ROLE_OPERATOR');

  deleteConfirmUsername = signal('');
  showDeleteSelfModal = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(
    private apiService: ApiService,
    public authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUsername.set(localStorage.getItem('username') || '');
    this.currentRole.set(this.authService.getRole() || 'ROLE_ADMIN');
    this.loadUsers();
  }

  isSuperAdmin(): boolean {
    const user = this.currentUsername().toLowerCase();
    return user === 'kaushik' || user === 'admin';
  }

  canDeleteUser(u: any): boolean {
    if (!u || u.username === 'kaushik') return false;
    // Super admin (kaushik) can delete any other account
    if (this.isSuperAdmin()) return true;
    // Regular admin cannot delete another admin
    const targetRole = (u.role || '').toUpperCase();
    if (targetRole === 'ROLE_ADMIN') {
      return false;
    }
    return true;
  }

  loadUsers(): void {
    this.apiService.getUsers().subscribe({
      next: u => {
        this.users.set(u || []);
      },
      error: () => {}
    });
  }

  onCreate(): void {
    const username = this.newUserName().trim();
    const password = this.newUserPass().trim();
    if (!username || !password) return;

    this.errorMessage.set(null);
    this.apiService.createUser({ username, password, role: this.newUserRole() }).subscribe({
      next: () => {
        this.loadUsers();
        this.newUserName.set('');
        this.newUserPass.set('');
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.error || 'Failed to create user account');
      }
    });
  }

  onDelete(id: number): void {
    this.errorMessage.set(null);
    this.apiService.deleteUser(id).subscribe({
      next: () => this.loadUsers(),
      error: (err) => {
        this.errorMessage.set(err?.error?.error || 'Only Super Admin (kaushik) can remove other Admin accounts.');
      }
    });
  }

  onDeleteSelf(): void {
    if (this.currentUsername().toLowerCase() === 'kaushik') {
      this.errorMessage.set('The primary Super Admin account (kaushik) cannot be deleted.');
      this.showDeleteSelfModal.set(false);
      return;
    }

    this.authService.deleteSelfAccount().subscribe({
      next: () => {
        this.authService.logout();
        this.router.navigate(['/home']);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.error || 'Failed to delete account');
        this.showDeleteSelfModal.set(false);
      }
    });
  }
}

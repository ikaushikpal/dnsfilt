import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.css'
})
export class UserManagementComponent implements OnInit {
  users = signal<any[]>([
    { id: 1, username: 'admin', role: 'ROLE_ADMIN' },
    { id: 2, username: 'operator1', role: 'ROLE_OPERATOR' },
    { id: 3, username: 'sec_auditor', role: 'ROLE_OPERATOR' }
  ]);

  newUserName = signal('');
  newUserPass = signal('');
  newUserRole = signal('ROLE_OPERATOR');

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.apiService.getUsers().subscribe({ next: u => this.users.set(u), error: () => {} });
  }

  onCreate(): void {
    const username = this.newUserName().trim();
    const password = this.newUserPass().trim();
    if (!username || !password) return;

    const newUser = { id: Date.now(), username, role: this.newUserRole() };
    this.users.set([...this.users(), newUser]);

    this.apiService.createUser({ username, password, role: this.newUserRole() }).subscribe({
      next: () => this.loadUsers(),
      error: () => {}
    });

    this.newUserName.set('');
    this.newUserPass.set('');
  }

  onDelete(id: number): void {
    this.users.set(this.users().filter(u => u.id !== id));

    this.apiService.deleteUser(id).subscribe({
      next: () => this.loadUsers(),
      error: () => {}
    });
  }
}

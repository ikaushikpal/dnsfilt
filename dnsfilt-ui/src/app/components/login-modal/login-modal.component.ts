import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-login-modal',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (show()) {
      <div class="fixed inset-0 z-50 bg-stone-950/80 backdrop-blur-sm flex items-center justify-center p-4 font-sans">
        <div class="bg-stone-900 border border-stone-800 rounded-xl p-6 max-w-sm w-full space-y-4 shadow-2xl">
          <div class="flex justify-between items-center">
            <h3 class="text-md font-mono font-bold text-white">Sign In to Admin Panel</h3>
            <button (click)="close.emit()" class="text-stone-400 hover:text-white font-mono">✕</button>
          </div>

          <form (ngSubmit)="onSubmit()" class="space-y-3">
            <div>
              <label class="text-xs font-mono text-stone-400 block mb-1">Username</label>
              <input 
                type="text" 
                [ngModel]="username()" 
                (ngModelChange)="username.set($event)" 
                name="loginUsername" 
                required 
                class="w-full bg-stone-950 border border-stone-800 rounded px-3 py-2 text-xs text-white font-mono focus:outline-none focus:border-amber-400">
            </div>
            <div>
              <label class="text-xs font-mono text-stone-400 block mb-1">Password</label>
              <input 
                type="password" 
                [ngModel]="password()" 
                (ngModelChange)="password.set($event)" 
                name="loginPassword" 
                required 
                class="w-full bg-stone-950 border border-stone-800 rounded px-3 py-2 text-xs text-white font-mono focus:outline-none focus:border-amber-400">
            </div>
            <button type="submit" class="w-full bg-amber-400 hover:bg-amber-300 text-stone-950 font-mono font-bold py-2 rounded text-xs transition">
              Sign In
            </button>
          </form>
        </div>
      </div>
    }
  `
})
export class LoginModalComponent {
  show = signal(false);

  @Input('show') set setShow(val: boolean) { this.show.set(val); }

  @Output() close = new EventEmitter<void>();
  @Output() login = new EventEmitter<{ username: string; password: string }>();

  username = signal('admin');
  password = signal('admin123');

  onSubmit(): void {
    const u = this.username().trim();
    const p = this.password().trim();
    if (!u || !p) return;
    this.login.emit({ username: u, password: p });
  }
}

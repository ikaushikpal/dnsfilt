import { Component, EventEmitter, Output, signal } from '@angular/core';

@Component({
  selector: 'app-shell-shortcuts',
  standalone: true,
  template: `
    <section class="bg-stone-900/60 border border-stone-800 rounded-xl p-6 space-y-4 font-sans">
      <h3 class="text-md font-mono font-bold text-white flex items-center gap-2">
        <i class="fa-solid fa-rocket text-amber-400"></i> Terminal Shortcut Functions
      </h3>
      <p class="text-xs text-stone-400">
        Add a shortcut command <code class="text-amber-400 font-mono">dy</code> to your shell profile to query your local <code class="text-emerald-400 font-mono">dnsfilt-resolver</code> instantly:
      </p>

      <!-- Shell Selector Tabs -->
      <div class="flex border-b border-stone-800 text-xs font-mono">
        <button (click)="activeShell.set('bash')" [class.border-b-2]="activeShell() === 'bash'" [class.border-amber-400]="activeShell() === 'bash'" [class.text-amber-400]="activeShell() === 'bash'" [class.font-bold]="activeShell() === 'bash'" [class.text-stone-400]="activeShell() !== 'bash'" class="px-4 py-2 transition">
          Bash
        </button>
        <button (click)="activeShell.set('fish')" [class.border-b-2]="activeShell() === 'fish'" [class.border-amber-400]="activeShell() === 'fish'" [class.text-amber-400]="activeShell() === 'fish'" [class.font-bold]="activeShell() === 'fish'" [class.text-stone-400]="activeShell() !== 'fish'" class="px-4 py-2 transition">
          Fish
        </button>
        <button (click)="activeShell.set('zsh')" [class.border-b-2]="activeShell() === 'zsh'" [class.border-amber-400]="activeShell() === 'zsh'" [class.text-amber-400]="activeShell() === 'zsh'" [class.font-bold]="activeShell() === 'zsh'" [class.text-stone-400]="activeShell() !== 'zsh'" class="px-4 py-2 transition">
          Zsh
        </button>
      </div>

      <!-- Shell Code Snippet using modern @switch -->
      <div class="bg-stone-950 border border-stone-800 rounded-lg p-4 font-mono text-xs space-y-2">
        @switch (activeShell()) {
          @case ('bash') {
            <div class="space-y-1">
              <p class="text-stone-500"># Add to ~/.bashrc</p>
              <div class="flex justify-between items-center">
                <code class="text-emerald-400">alias dy="dig +short &#64;127.0.0.1 -p 2053"</code>
                <button (click)="copy.emit('alias dy=\&quot;dig +short @127.0.0.1 -p 2053\&quot;')" class="text-[10px] bg-stone-800 hover:bg-stone-700 text-stone-300 px-2.5 py-1 rounded border border-stone-700 flex items-center gap-1">
                  <i class="fa-solid fa-copy"></i> Copy
                </button>
              </div>
            </div>
          }
          @case ('fish') {
            <div class="space-y-1">
              <p class="text-stone-500"># Add to ~/.config/fish/config.fish</p>
              <div class="flex justify-between items-center">
                <code class="text-emerald-400">alias dy="dig +noall +answer +additional $argv &#64;127.0.0.1 -p 2053"</code>
                <button (click)="copy.emit('alias dy=\&quot;dig +noall +answer +additional $argv @127.0.0.1 -p 2053\&quot;')" class="text-[10px] bg-stone-800 hover:bg-stone-700 text-stone-300 px-2.5 py-1 rounded border border-stone-700 flex items-center gap-1">
                  <i class="fa-solid fa-copy"></i> Copy
                </button>
              </div>
            </div>
          }
          @case ('zsh') {
            <div class="space-y-1">
              <p class="text-stone-500"># Add to ~/.zshrc</p>
              <div class="flex justify-between items-center">
                <code class="text-emerald-400">alias dy="dig +short &#64;127.0.0.1 -p 2053"</code>
                <button (click)="copy.emit('alias dy=\&quot;dig +short @127.0.0.1 -p 2053\&quot;')" class="text-[10px] bg-stone-800 hover:bg-stone-700 text-stone-300 px-2.5 py-1 rounded border border-stone-700 flex items-center gap-1">
                  <i class="fa-solid fa-copy"></i> Copy
                </button>
              </div>
            </div>
          }
        }
      </div>
    </section>
  `
})
export class ShellShortcutsComponent {
  @Output() copy = new EventEmitter<string>();

  activeShell = signal<'bash' | 'fish' | 'zsh'>('bash');
}

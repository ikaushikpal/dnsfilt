import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

export type OsGuide = 'macos' | 'windows' | 'linux' | 'ios' | 'android' | 'router';

@Component({
  selector: 'app-guide',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './guide.component.html',
  styles: []
})
export class GuideComponent {
  selectedOs = signal<OsGuide>('macos');
  copied = signal<string | null>(null);

  copyText(text: string, key: string): void {
    navigator.clipboard.writeText(text);
    this.copied.set(key);
    setTimeout(() => this.copied.set(null), 2000);
  }
}

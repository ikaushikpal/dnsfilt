import { Component, Input, signal } from '@angular/core';

@Component({
  selector: 'app-toast',
  standalone: true,
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.css'
})
export class ToastComponent {
  message = signal<string | null>(null);

  @Input('message') set setMessage(val: string | null) {
    this.message.set(val);
  }
}

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  constructor(private router: Router) {}

  scrollToDnsGuide(): void {
    const el = document.getElementById('dns-guide-section');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }

  navigateToCli(): void {
    this.router.navigate(['/services']);
  }
}

import { Component } from '@angular/core';

/** Admin dashboard. Guarded by authGuard — unreachable without a valid JWT. */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  template: `
    <main>
      <h1>Admin dashboard</h1>
      <!-- Aircraft, anomaly, baseline management (Phase 8) -->
    </main>
  `,
})
export class AdminDashboardComponent {}

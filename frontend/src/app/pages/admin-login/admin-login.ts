import { Component } from '@angular/core';

/** Admin sign-in. Lives only under /admin — public routes never import this. */
@Component({
  selector: 'app-admin-login',
  standalone: true,
  template: `
    <main>
      <h1>Admin sign-in</h1>
      <!-- Login form (Phase 8) -->
    </main>
  `,
})
export class AdminLoginComponent {}

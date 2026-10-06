import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { backendErrorMessage } from '../../components/admin/admin-http';

/**
 * Admin sign-in. Lives only under /admin/login — public routes never
 * import this. An already-authenticated session skips the form and goes
 * straight to the dashboard.
 */
@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './admin-login.scss',
  template: `
    <main class="login-page">
      <a class="back-link" routerLink="/map" aria-label="Back to the public map">
        &larr; Public map
      </a>

      <section class="login-card" aria-labelledby="login-heading">
        <span class="card-mark" aria-hidden="true">◈</span>
        <h1 id="login-heading">Operator sign-in</h1>
        <p class="sub">Restricted area. Authorized operators only.</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="username">Username</label>
            <input
              id="username"
              type="text"
              formControlName="username"
              autocomplete="username"
              required
              aria-required="true"
              [attr.aria-invalid]="form.controls.username.touched && form.controls.username.invalid"
            />
            @if (form.controls.username.touched && form.controls.username.invalid) {
              <p class="field-error" role="alert">Username is required.</p>
            }
          </div>

          <div class="field">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="current-password"
              required
              aria-required="true"
              [attr.aria-invalid]="form.controls.password.touched && form.controls.password.invalid"
            />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <p class="field-error" role="alert">Password is required.</p>
            }
          </div>

          @if (error()) {
            <p class="form-error" role="alert">{{ error() }}</p>
          }

          <button type="submit" [disabled]="submitting()" aria-label="Sign in to the admin dashboard">
            {{ submitting() ? 'Signing in\u2026' : 'Sign in' }}
          </button>
        </form>
      </section>
    </main>
  `,
})
export class AdminLoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // Already signed in? Skip the form entirely.
    if (this.auth.isAuthenticated()) {
      void this.router.navigate(['/admin']);
    }
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/admin']);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(backendErrorMessage(err, 'Sign-in failed. Please try again.'));
      },
    });
  }
}

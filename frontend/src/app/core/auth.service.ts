import { Injectable, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

import { ApiService } from './api.service';

const TOKEN_KEY = 'sentinel_token';

/**
 * JWT session management. The token lives in sessionStorage (not
 * localStorage) so it dies with the tab — a deliberate trade-off:
 * slightly less convenient, meaningfully safer against XSS persistence.
 *
 * Public components never inject this service. Only admin routes do.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly tokenSig = signal<string | null>(sessionStorage.getItem(TOKEN_KEY));
  readonly isAuthenticated = computed(() => this.tokenSig() !== null);

  get token(): string | null {
    return this.tokenSig();
  }

  login(username: string, password: string) {
    return this.api.login(username, password).pipe(
      tap((res) => {
        sessionStorage.setItem(TOKEN_KEY, res.token);
        this.tokenSig.set(res.token);
      }),
    );
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    this.tokenSig.set(null);
    this.router.navigate(['/map']);
  }
}

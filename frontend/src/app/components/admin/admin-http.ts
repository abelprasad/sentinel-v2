import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { ErrorDto } from '../../core/models/api.models';

/**
 * 401 handling shared by every admin component. An expired or revoked JWT
 * clears the session via AuthService.logout() and bounces the user to the
 * admin sign-in page. Returns true when the error was a 401 (i.e. handled).
 */
export function handleExpiredSession(err: unknown, auth: AuthService, router: Router): boolean {
  if (err instanceof HttpErrorResponse && err.status === 401) {
    auth.logout();
    void router.navigate(['/admin/login']);
    return true;
  }
  return false;
}

/** Pulls a human-readable message out of the backend's uniform ErrorDto. */
export function backendErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ErrorDto> | string | null | undefined;
    if (typeof body === 'string' && body.trim().length > 0) {
      return body;
    }
    if (body && typeof body.message === 'string' && body.message.trim().length > 0) {
      return body.message;
    }
    if (err.status === 0) {
      return 'Could not reach the server. Check your connection and try again.';
    }
  }
  return fallback;
}

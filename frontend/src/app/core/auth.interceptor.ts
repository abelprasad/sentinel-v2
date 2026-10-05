import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService } from './auth.service';

/**
 * Single HTTP interceptor for the whole app. Attaches the JWT bearer
 * token to /api/admin/* requests only — public endpoints never get a
 * token, even if one happens to be stored.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  if (!req.url.startsWith('/api/admin')) {
    return next(req);
  }

  const token = auth.token;
  if (!token) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

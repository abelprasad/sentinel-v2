import { CanActivateFn } from '@angular/router';

/**
 * Placeholder guard. The real implementation (JWT check) lands with the
 * auth service. Until then, admin routes are unreachable by default.
 */
export const authGuard: CanActivateFn = () => false;

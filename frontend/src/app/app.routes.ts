import { Routes } from '@angular/router';

import { PublicMapComponent } from './pages/public-map/public-map';
import { AdminLoginComponent } from './pages/admin-login/admin-login';
import { AdminDashboardComponent } from './pages/admin-dashboard/admin-dashboard';
import { ReplayComponent } from './components/replay/replay';
import { authGuard } from './core/auth-guard';

/**
 * Route layout:
 * - Public routes (/map, /replay) never touch auth — no token reads, no redirects.
 * - Auth lives only under /admin.
 * - Wildcard falls back to the public map, so a redirect loop is
 *   impossible by construction (this was v1's infinite-redirect bug).
 */
export const routes: Routes = [
  { path: '', redirectTo: 'map', pathMatch: 'full' },
  { path: 'map', component: PublicMapComponent },
  { path: 'replay', component: ReplayComponent },
  { path: 'admin/login', component: AdminLoginComponent },
  { path: 'admin', component: AdminDashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'map' },
];

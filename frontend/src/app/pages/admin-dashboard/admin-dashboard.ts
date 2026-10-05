import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { StatusDto } from '../../core/models/api.models';
import { backendErrorMessage, handleExpiredSession } from '../../components/admin/admin-http';
import { AircraftTableComponent } from '../../components/admin/aircraft-table/aircraft-table';

type AdminTab = 'overview' | 'aircraft' | 'anomalies' | 'baselines';

const TABS: ReadonlyArray<{ id: AdminTab; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'aircraft', label: 'Aircraft' },
  { id: 'anomalies', label: 'Anomalies' },
  { id: 'baselines', label: 'Baselines' },
];

/**
 * Admin dashboard shell. Guarded by authGuard — unreachable without a
 * valid JWT. Tab content components are wired in by their own commits;
 * until then their tabs render a placeholder.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [DatePipe, AircraftTableComponent],
  styleUrl: './admin-dashboard.scss',
  template: `
    <div class="admin-shell">
      <header class="admin-header">
        <div class="brand">
          <h1>SENTINEL admin</h1>
        </div>
        <button type="button" class="logout-btn" (click)="logout()" aria-label="Sign out of the admin dashboard">
          Sign out
        </button>
      </header>

      <nav class="tab-bar" role="tablist" aria-label="Admin sections">
        @for (tab of tabs; track tab.id) {
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="activeTab() === tab.id"
            [class.active]="activeTab() === tab.id"
            (click)="selectTab(tab.id)"
          >
            {{ tab.label }}
          </button>
        }
      </nav>

      <main class="tab-panel" role="tabpanel">
        @switch (activeTab()) {
          @case ('overview') {
            <section aria-labelledby="overview-heading">
              <div class="panel-head">
                <h2 id="overview-heading">System status</h2>
                <button type="button" class="ghost-btn" (click)="loadStatus()" [disabled]="loading()" aria-label="Refresh system status">
                  {{ loading() ? 'Refreshing\u2026' : 'Refresh' }}
                </button>
              </div>

              @if (loading() && !status()) {
                <p class="muted">Loading status\u2026</p>
              }
              @if (statusError()) {
                <p class="panel-error" role="alert">{{ statusError() }}</p>
              }
              @if (status(); as s) {
                <dl class="stat-grid">
                  <div class="stat"><dt>Service</dt><dd>{{ s.service }}</dd></div>
                  <div class="stat"><dt>Status</dt><dd>{{ s.status }}</dd></div>
                  <div class="stat"><dt>Aircraft tracked</dt><dd>{{ s.aircraftTracked }}</dd></div>
                  <div class="stat"><dt>Active tracks</dt><dd>{{ s.activeTracks }}</dd></div>
                  <div class="stat"><dt>Events (last hour)</dt><dd>{{ s.eventsLastHour }}</dd></div>
                  <div class="stat"><dt>Unacknowledged anomalies</dt><dd>{{ s.unacknowledgedAnomalies }}</dd></div>
                  <div class="stat"><dt>Anomalies (24h)</dt><dd>{{ s.anomaliesLast24h }}</dd></div>
                  <div class="stat"><dt>As of</dt><dd>{{ s.time | date: 'medium' }}</dd></div>
                </dl>
              }
            </section>
          }
          @case ('aircraft') {
            <app-aircraft-table />
          }
          @case ('anomalies') {
            <p class="muted placeholder">Anomaly management lands here.</p>
          }
          @case ('baselines') {
            <p class="muted placeholder">Baseline management lands here.</p>
          }
        }
      </main>
    </div>
  `,
})
export class AdminDashboardComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly tabs = TABS;
  readonly activeTab = signal<AdminTab>('overview');

  readonly status = signal<StatusDto | null>(null);
  readonly loading = signal(false);
  readonly statusError = signal<string | null>(null);

  constructor() {
    this.loadStatus();
  }

  selectTab(tab: AdminTab): void {
    this.activeTab.set(tab);
  }

  logout(): void {
    this.auth.logout();
  }

  loadStatus(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.statusError.set(null);
    this.api.getStatus().subscribe({
      next: (s) => {
        this.loading.set(false);
        this.status.set(s);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.statusError.set(backendErrorMessage(err, 'Could not load system status.'));
        }
      },
    });
  }
}

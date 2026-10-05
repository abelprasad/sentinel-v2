import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';

import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { AnomalyDto, PagedResponse } from '../../../core/models/api.models';
import { backendErrorMessage, handleExpiredSession } from '../admin-http';

const PAGE_SIZE = 20;

/** Renders one z-score the same way the public anomaly view does. */
function formatZ(value: number | null): string {
  if (value === null || value === undefined) {
    return '\u2014';
  }
  const sign = value >= 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}\u03c3`;
}

/**
 * Admin anomaly management: paged anomaly list with the same z-score
 * detail as the public view, plus the operator workflow — acknowledge
 * (marks reviewed), escalate, and de-escalate. A filter narrows the list
 * to unacknowledged anomalies.
 */
@Component({
  selector: 'app-anomaly-list',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  styleUrl: './anomaly-list.scss',
  template: `
    <section aria-labelledby="anomalies-heading">
      <div class="panel-head">
        <h2 id="anomalies-heading">Anomalies</h2>
        <div class="panel-tools">
          <label class="filter-toggle">
            <input
              type="checkbox"
              [checked]="unacknowledgedOnly()"
              (change)="unacknowledgedOnly.set($any($event.target).checked)"
              aria-label="Show unacknowledged anomalies only"
            />
            Unacknowledged only
          </label>
          <button
            type="button"
            class="ghost-btn"
            (click)="load()"
            [disabled]="loading()"
            aria-label="Refresh anomaly list"
          >
            {{ loading() ? 'Refreshing\u2026' : 'Refresh' }}
          </button>
        </div>
      </div>

      @if (error()) {
        <p class="panel-error" role="alert">{{ error() }}</p>
      }

      @if (loading() && rows().length === 0) {
        <p class="muted">Loading anomalies\u2026</p>
      } @else if (visibleRows().length === 0) {
        <p class="muted empty">
          {{ unacknowledgedOnly() ? 'No unacknowledged anomalies.' : 'No anomalies found.' }}
        </p>
      } @else {
        <ol class="anomaly-list">
          @for (a of visibleRows(); track a.id) {
            <li class="anomaly-card" [class.escalated]="a.escalated">
              <div class="card-top">
                <div class="card-id">
                  <span class="mono">{{ a.icaoHex }}</span>
                  <span class="callsign">{{ a.callsign ?? 'no callsign' }}</span>
                  <span class="muted">#{{ a.id }}</span>
                </div>
                <div class="badges">
                  @if (a.acknowledged) {
                    <span class="badge ack">Reviewed</span>
                  } @else {
                    <span class="badge new">New</span>
                  }
                  @if (a.escalated) {
                    <span class="badge esc">Escalated</span>
                  }
                </div>
              </div>

              <div class="score-row">
                <span class="score" aria-label="Anomaly score {{ a.score | number: '1.2-2' }}">
                  Score <strong>{{ a.score | number: '1.2-2' }}</strong>
                </span>
                <span class="muted">{{ a.flaggedAt | date: 'medium' }}</span>
              </div>

              <dl class="zscores" aria-label="Z-score detail">
                <div><dt>Altitude</dt><dd>{{ z(a.zAltitude) }}</dd></div>
                <div><dt>Speed</dt><dd>{{ z(a.zSpeed) }}</dd></div>
                <div><dt>Heading</dt><dd>{{ z(a.zHeading) }}</dd></div>
                <div><dt>Position</dt><dd>{{ z(a.zPosition) }}</dd></div>
              </dl>

              @if (a.explanation) {
                <p class="explanation">{{ a.explanation }}</p>
              }

              <div class="card-actions">
                <button
                  type="button"
                  class="ghost-btn"
                  (click)="acknowledge(a)"
                  [disabled]="a.acknowledged || busy(a.id)"
                  [attr.aria-label]="'Acknowledge anomaly ' + a.id"
                >
                  {{ a.acknowledged ? 'Acknowledged' : busy(a.id) ? 'Working\u2026' : 'Acknowledge' }}
                </button>
                <button
                  type="button"
                  [class]="a.escalated ? 'ghost-btn' : 'warn-btn'"
                  (click)="toggleEscalation(a)"
                  [disabled]="busy(a.id)"
                  [attr.aria-label]="(a.escalated ? 'De-escalate anomaly ' : 'Escalate anomaly ') + a.id"
                  [attr.aria-pressed]="a.escalated"
                >
                  {{ a.escalated ? 'De-escalate' : 'Escalate' }}
                </button>
              </div>
            </li>
          }
        </ol>

        <div class="pager">
          <button type="button" class="ghost-btn" (click)="prevPage()" [disabled]="page() === 0 || loading()" aria-label="Previous page">
            &larr; Prev
          </button>
          <span class="muted" aria-live="polite">Page {{ page() + 1 }} of {{ totalPages() }}</span>
          <button
            type="button"
            class="ghost-btn"
            (click)="nextPage()"
            [disabled]="page() + 1 >= totalPages() || loading()"
            aria-label="Next page"
          >
            Next &rarr;
          </button>
        </div>
      }
    </section>
  `,
})
export class AnomalyListComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly rows = signal<AnomalyDto[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly unacknowledgedOnly = signal(false);
  readonly busyIds = signal<ReadonlySet<number>>(new Set());

  readonly visibleRows = computed(() =>
    this.unacknowledgedOnly() ? this.rows().filter((a) => !a.acknowledged) : this.rows(),
  );

  constructor() {
    this.load();
  }

  z(value: number | null): string {
    return formatZ(value);
  }

  busy(id: number): boolean {
    return this.busyIds().has(id);
  }

  load(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.getAnomalies(this.page(), PAGE_SIZE).subscribe({
      next: (res: PagedResponse<AnomalyDto>) => {
        this.loading.set(false);
        this.rows.set(res.content);
        this.totalPages.set(Math.max(1, res.totalPages));
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, 'Could not load anomalies.'));
        }
      },
    });
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((p) => p + 1);
      this.load();
    }
  }

  acknowledge(row: AnomalyDto): void {
    if (row.acknowledged || this.busy(row.id)) {
      return;
    }
    this.markBusy(row.id, true);
    this.error.set(null);
    this.api.acknowledgeAnomaly(row.id).subscribe({
      next: (updated) => {
        this.markBusy(row.id, false);
        this.replaceRow(updated);
      },
      error: (err: unknown) => {
        this.markBusy(row.id, false);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, `Could not acknowledge anomaly #${row.id}.`));
        }
      },
    });
  }

  toggleEscalation(row: AnomalyDto): void {
    if (this.busy(row.id)) {
      return;
    }
    this.markBusy(row.id, true);
    this.error.set(null);
    const action = row.escalated
      ? this.api.deEscalateAnomaly(row.id)
      : this.api.escalateAnomaly(row.id);
    action.subscribe({
      next: (updated) => {
        this.markBusy(row.id, false);
        this.replaceRow(updated);
      },
      error: (err: unknown) => {
        this.markBusy(row.id, false);
        const verb = row.escalated ? 'de-escalate' : 'escalate';
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, `Could not ${verb} anomaly #${row.id}.`));
        }
      },
    });
  }

  private replaceRow(updated: AnomalyDto): void {
    this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
  }

  private markBusy(id: number, busy: boolean): void {
    this.busyIds.update((prev) => {
      const next = new Set(prev);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}

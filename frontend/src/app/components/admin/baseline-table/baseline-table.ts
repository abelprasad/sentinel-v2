import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';

import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { BaselineDto, PagedResponse } from '../../../core/models/api.models';
import { backendErrorMessage, handleExpiredSession } from '../admin-http';

const PAGE_SIZE = 20;

/**
 * Admin baseline management: per-aircraft rolling baseline stats
 * (sample counts and means) with a guarded reset action.
 *
 * CONTRACT GAP (flagged to the orchestrator): the backend BaselineDto
 * currently serializes different field names (avgAltitudeFt,
 * altitudeCount, …) and no aircraftId, while the frontend model used here
 * declares aircraftId / sampleCount / meanAltitudeFt / …. The component
 * is written against the frontend model per the API-service contract; a
 * runtime guard refuses to reset when the aircraft id is absent.
 */
@Component({
  selector: 'app-baseline-table',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  styleUrl: './baseline-table.scss',
  template: `
    <section aria-labelledby="baselines-heading">
      <div class="panel-head">
        <h2 id="baselines-heading">Baselines</h2>
        <button
          type="button"
          class="ghost-btn"
          (click)="load()"
          [disabled]="loading()"
          aria-label="Refresh baseline list"
        >
          {{ loading() ? 'Refreshing\u2026' : 'Refresh' }}
        </button>
      </div>

      @if (error()) {
        <p class="panel-error" role="alert">{{ error() }}</p>
      }
      @if (notice()) {
        <p class="panel-notice" role="status">{{ notice() }}</p>
      }

      @if (loading() && rows().length === 0) {
        <p class="muted">Loading baselines\u2026</p>
      } @else {
        <div class="table-wrap">
          <table>
            <caption class="sr-only">
              Rolling baselines per aircraft. Columns: ICAO hex, samples, mean altitude, mean speed,
              mean heading, last updated, actions.
            </caption>
            <thead>
              <tr>
                <th scope="col">ICAO</th>
                <th scope="col">Samples</th>
                <th scope="col">Mean altitude (ft)</th>
                <th scope="col">Mean speed (kts)</th>
                <th scope="col">Mean heading (&deg;)</th>
                <th scope="col">Last updated</th>
                <th scope="col"><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              @for (b of rows(); track b.icaoHex) {
                <tr>
                  <td class="mono">{{ b.icaoHex }}</td>
                  <td>{{ b.sampleCount | number }}</td>
                  <td>{{ b.meanAltitudeFt === null ? '\u2014' : (b.meanAltitudeFt | number: '1.0-0') }}</td>
                  <td>{{ b.meanSpeedKts === null ? '\u2014' : (b.meanSpeedKts | number: '1.1-1') }}</td>
                  <td>{{ b.meanHeadingDeg === null ? '\u2014' : (b.meanHeadingDeg | number: '1.1-1') }}</td>
                  <td>{{ b.lastUpdated | date: 'medium' }}</td>
                  <td class="actions">
                    <button
                      type="button"
                      class="warn-btn"
                      (click)="requestReset(b)"
                      [attr.aria-label]="'Reset baseline for aircraft ' + b.icaoHex"
                    >
                      Reset
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="7" class="muted">No baselines yet.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

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

      @if (pendingReset(); as target) {
        <div class="dialog-backdrop">
          <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="reset-title" aria-describedby="reset-desc">
            <h3 id="reset-title">Reset baseline?</h3>
            <p id="reset-desc">
              This wipes the rolling baseline for aircraft
              <strong class="mono">{{ target.icaoHex }}</strong>. The next track events will
              rebuild it from scratch. Use this for poisoned baselines (e.g. a track that was
              spoofing and has since gone legitimate).
            </p>
            <div class="dialog-actions">
              <button type="button" class="ghost-btn" (click)="cancelReset()" [disabled]="resetting()">
                Cancel
              </button>
              <button
                type="button"
                class="warn-btn solid"
                (click)="confirmReset()"
                [disabled]="resetting()"
                [attr.aria-label]="'Confirm baseline reset for aircraft ' + target.icaoHex"
              >
                {{ resetting() ? 'Resetting\u2026' : 'Reset baseline' }}
              </button>
            </div>
          </div>
        </div>
      }
    </section>
  `,
})
export class BaselineTableComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly rows = signal<BaselineDto[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly pendingReset = signal<BaselineDto | null>(null);
  readonly resetting = signal(false);

  constructor() {
    this.load();
  }

  load(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.getBaselines(this.page(), PAGE_SIZE).subscribe({
      next: (res: PagedResponse<BaselineDto>) => {
        this.loading.set(false);
        this.rows.set(res.content);
        this.totalPages.set(Math.max(1, res.totalPages));
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, 'Could not load baselines.'));
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

  requestReset(row: BaselineDto): void {
    this.notice.set(null);
    this.pendingReset.set(row);
  }

  cancelReset(): void {
    if (!this.resetting()) {
      this.pendingReset.set(null);
    }
  }

  confirmReset(): void {
    const target = this.pendingReset();
    if (!target || this.resetting()) {
      return;
    }

    // Defensive: the reset endpoint needs the numeric aircraft id.
    const aircraftId: unknown = target.aircraftId;
    if (typeof aircraftId !== 'number' || !Number.isFinite(aircraftId)) {
      this.pendingReset.set(null);
      this.error.set(
        `Cannot reset the baseline for ${target.icaoHex}: the API response did not include an aircraft id.`,
      );
      return;
    }

    this.resetting.set(true);
    this.error.set(null);
    this.api.resetBaseline(aircraftId).subscribe({
      next: () => {
        this.resetting.set(false);
        this.pendingReset.set(null);
        this.notice.set(`Baseline reset for ${target.icaoHex}. It will rebuild from new track events.`);
        this.load();
      },
      error: (err: unknown) => {
        this.resetting.set(false);
        this.pendingReset.set(null);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, `Could not reset the baseline for ${target.icaoHex}.`));
        }
      },
    });
  }
}

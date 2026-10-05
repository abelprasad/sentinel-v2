import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';

import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { AircraftDto, PagedResponse } from '../../../core/models/api.models';
import { backendErrorMessage, handleExpiredSession } from '../admin-http';

const PAGE_SIZE = 20;

/**
 * Resolves the numeric aircraft id the DELETE endpoint needs.
 *
 * CONTRACT GAP (flagged to the orchestrator): the admin aircraft list DTO
 * carries no numeric id — only ICAO hex — while
 * ApiService.deleteAircraft(id: number) requires one. If the backend ever
 * includes `id` in the DTO this works as-is; until then the delete action
 * reports the gap instead of sending a bogus request.
 */
function aircraftRowId(row: AircraftDto): number | null {
  const id = (row as unknown as { id?: unknown }).id;
  return typeof id === 'number' && Number.isFinite(id) ? id : null;
}

/**
 * Admin aircraft management: paged table of tracked aircraft with a
 * guarded delete action (confirmation dialog, then DELETE).
 */
@Component({
  selector: 'app-aircraft-table',
  standalone: true,
  imports: [DatePipe],
  styleUrl: './aircraft-table.scss',
  template: `
    <section aria-labelledby="aircraft-heading">
      <div class="panel-head">
        <h2 id="aircraft-heading">Aircraft</h2>
        <button
          type="button"
          class="ghost-btn"
          (click)="load()"
          [disabled]="loading()"
          aria-label="Refresh aircraft list"
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
        <p class="muted">Loading aircraft\u2026</p>
      } @else {
        <div class="table-wrap">
          <table>
            <caption class="sr-only">
              Tracked aircraft. Columns: ICAO hex, callsign, track state, first seen, last seen, actions.
            </caption>
            <thead>
              <tr>
                <th scope="col">ICAO</th>
                <th scope="col">Callsign</th>
                <th scope="col">Track state</th>
                <th scope="col">First seen</th>
                <th scope="col">Last seen</th>
                <th scope="col"><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              @for (a of rows(); track a.icaoHex) {
                <tr>
                  <td class="mono">{{ a.icaoHex }}</td>
                  <td>{{ a.callsign ?? '\u2014' }}</td>
                  <td><span class="badge">{{ a.trackState }}</span></td>
                  <td>{{ a.firstSeen | date: 'medium' }}</td>
                  <td>{{ a.lastSeen | date: 'medium' }}</td>
                  <td class="actions">
                    <button
                      type="button"
                      class="danger-btn"
                      (click)="requestDelete(a)"
                      [attr.aria-label]="'Delete aircraft ' + a.icaoHex"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="muted">No aircraft tracked.</td>
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

      @if (pendingDelete(); as target) {
        <div class="dialog-backdrop">
          <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="delete-title" aria-describedby="delete-desc">
            <h3 id="delete-title">Delete aircraft?</h3>
            <p id="delete-desc">
              This permanently removes aircraft <strong class="mono">{{ target.icaoHex }}</strong>
              ({{ target.callsign ?? 'no callsign' }}) and its track history. This cannot be undone.
            </p>
            <div class="dialog-actions">
              <button type="button" class="ghost-btn" (click)="cancelDelete()" [disabled]="deleting()">
                Cancel
              </button>
              <button
                type="button"
                class="danger-btn solid"
                (click)="confirmDelete()"
                [disabled]="deleting()"
                [attr.aria-label]="'Confirm deletion of aircraft ' + target.icaoHex"
              >
                {{ deleting() ? 'Deleting\u2026' : 'Delete aircraft' }}
              </button>
            </div>
          </div>
        </div>
      }
    </section>
  `,
})
export class AircraftTableComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly rows = signal<AircraftDto[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly pendingDelete = signal<AircraftDto | null>(null);
  readonly deleting = signal(false);

  constructor() {
    this.load();
  }

  load(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.getAircraft(this.page(), PAGE_SIZE).subscribe({
      next: (res: PagedResponse<AircraftDto>) => {
        this.loading.set(false);
        this.rows.set(res.content);
        this.totalPages.set(Math.max(1, res.totalPages));
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, 'Could not load aircraft.'));
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

  requestDelete(row: AircraftDto): void {
    this.notice.set(null);
    this.pendingDelete.set(row);
  }

  cancelDelete(): void {
    if (!this.deleting()) {
      this.pendingDelete.set(null);
    }
  }

  confirmDelete(): void {
    const target = this.pendingDelete();
    if (!target || this.deleting()) {
      return;
    }

    const id = aircraftRowId(target);
    if (id === null) {
      this.pendingDelete.set(null);
      this.error.set(
        `Cannot delete ${target.icaoHex}: the API response did not include a numeric aircraft id for this row.`,
      );
      return;
    }

    this.deleting.set(true);
    this.error.set(null);
    this.api.deleteAircraft(id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.pendingDelete.set(null);
        this.notice.set(`Deleted aircraft ${target.icaoHex}.`);
        // Stay on a valid page if the last row of the last page vanished.
        if (this.rows().length === 1 && this.page() > 0) {
          this.page.update((p) => p - 1);
        }
        this.load();
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDelete.set(null);
        if (!handleExpiredSession(err, this.auth, this.router)) {
          this.error.set(backendErrorMessage(err, `Could not delete ${target.icaoHex}.`));
        }
      },
    });
  }
}

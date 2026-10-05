import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AnomalyDto } from '../../core/models/api.models';
import { AnomalyDetailComponent } from './anomaly-detail';

/**
 * Paginated public anomaly list.
 *
 * Shows score badges color-coded by severity, callsign, relative time,
 * and acknowledged/escalated indicators. Expandable rows render
 * <app-anomaly-detail> (aria-expanded on the toggle). An optional ICAO
 * hex filter scopes the feed to one aircraft.
 *
 * Public component: calls only the unauthenticated ApiService methods.
 */
@Component({
  selector: 'app-anomaly-list',
  standalone: true,
  imports: [FormsModule, AnomalyDetailComponent],
  template: `
    <section aria-labelledby="anomaly-list-heading">
      <h2 id="anomaly-list-heading">Anomaly feed</h2>

      <form class="filter" (submit)="applyFilter(); $event.preventDefault()">
        <label for="icao-filter">Filter by aircraft (ICAO hex)</label>
        <input
          id="icao-filter"
          type="text"
          [(ngModel)]="icaoFilter"
          name="icaoFilter"
          placeholder="e.g. A1B2C3"
          maxlength="6"
          autocomplete="off"
          spellcheck="false"
        />
        <button type="submit">Apply</button>
        @if (activeIcao()) {
          <button type="button" (click)="clearFilter()">Clear {{ activeIcao() }}</button>
        }
      </form>

      @if (loading()) {
        <p class="status" role="status">Loading anomalies&hellip;</p>
      } @else if (error()) {
        <p class="status error" role="alert">{{ error() }}</p>
      } @else if (anomalies().length === 0) {
        <p class="status" role="status">No anomalies found.</p>
      } @else {
        <ul class="anomaly-list">
          @for (a of anomalies(); track a.id) {
            <li class="anomaly-card" [class.acknowledged]="a.acknowledged">
              <button
                type="button"
                class="row-toggle"
                (click)="toggle(a.id)"
                [attr.aria-expanded]="expandedId() === a.id"
                [attr.aria-label]="'Details for anomaly ' + a.id + ' (' + displayCallsign(a) + ')'"
              >
                <span class="score-badge" [class]="severityClass(a.score)" title="Anomaly score {{ a.score.toFixed(1) }}">
                  {{ a.score.toFixed(1) }}
                </span>
                <span class="main">
                  <span class="callsign">{{ displayCallsign(a) }}</span>
                  <span class="icao">{{ a.icaoHex }}</span>
                </span>
                <span class="meta">
                  <time [attr.datetime]="a.flaggedAt">{{ timeAgo(a.flaggedAt) }}</time>
                  @if (a.acknowledged) {
                    <span class="pill ack">ACK</span>
                  }
                  @if (a.escalated) {
                    <span class="pill esc">ESCALATED</span>
                  }
                </span>
                <span class="chevron" aria-hidden="true">{{ expandedId() === a.id ? '&#9662;' : '&#9656;' }}</span>
              </button>
              @if (expandedId() === a.id) {
                <app-anomaly-detail [anomaly]="a" />
              }
            </li>
          }
        </ul>

        <nav class="pagination" aria-label="Anomaly pages">
          <button type="button" (click)="prevPage()" [disabled]="page() === 0 || loading()">
            &larr; Prev
          </button>
          <span role="status" aria-live="polite">
            Page {{ page() + 1 }} of {{ totalPages() }} ({{ totalElements() }} total)
          </span>
          <button type="button" (click)="nextPage()" [disabled]="page() + 1 >= totalPages() || loading()">
            Next &rarr;
          </button>
        </nav>
      }
    </section>
  `,
  styles: [
    `
    h2 { font-size: 1.25rem; margin: 0 0 1rem; }
    .filter { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .filter label { font-size: 0.85rem; color: #8fa3bd; }
    .filter input { background: #0d1520; border: 1px solid #2a3a52; color: #eef3fa; padding: 0.4rem 0.6rem; border-radius: 0.3rem; width: 8rem; text-transform: uppercase; }
    .filter button { background: #182233; border: 1px solid #2a3a52; color: #dbe5f2; padding: 0.4rem 0.8rem; border-radius: 0.3rem; cursor: pointer; }
    .filter button:hover { background: #22314a; }
    .status { color: #8fa3bd; padding: 1rem 0; }
    .status.error { color: #ff8a8a; }
    .anomaly-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
    .anomaly-card { border: 1px solid #1e2a3a; border-radius: 0.5rem; overflow: hidden; background: #0a1119; }
    .anomaly-card.acknowledged { opacity: 0.72; }
    .row-toggle { width: 100%; display: flex; align-items: center; gap: 0.9rem; padding: 0.7rem 0.9rem; background: none; border: none; color: inherit; cursor: pointer; text-align: left; }
    .row-toggle:hover { background: #101b2a; }
    .score-badge { min-width: 3.2rem; text-align: center; font-weight: 800; font-variant-numeric: tabular-nums; padding: 0.35rem 0.5rem; border-radius: 0.35rem; font-size: 0.9rem; }
    .sev-low { background: #1c3a24; color: #7fd68f; }
    .sev-med { background: #4a3d12; color: #f4c542; }
    .sev-high { background: #54220f; color: #ff9a5a; }
    .sev-crit { background: #5f1515; color: #ff6b6b; }
    .main { display: flex; flex-direction: column; flex: 1; min-width: 0; }
    .callsign { font-weight: 700; color: #eef3fa; }
    .icao { font-size: 0.75rem; color: #66788f; font-family: monospace; }
    .meta { display: flex; align-items: center; gap: 0.5rem; font-size: 0.8rem; color: #8fa3bd; }
    .pill { font-size: 0.65rem; font-weight: 700; letter-spacing: 0.05em; padding: 0.15rem 0.45rem; border-radius: 0.25rem; }
    .pill.ack { background: #22314a; color: #9fc1e8; }
    .pill.esc { background: #5f1515; color: #ff8a8a; }
    .chevron { color: #66788f; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; font-size: 0.85rem; color: #8fa3bd; }
    .pagination button { background: #182233; border: 1px solid #2a3a52; color: #dbe5f2; padding: 0.4rem 0.9rem; border-radius: 0.3rem; cursor: pointer; }
    .pagination button:disabled { opacity: 0.4; cursor: default; }
    `,
  ],
})
export class AnomalyListComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private sub: Subscription | null = null;

  readonly anomalies = signal<AnomalyDto[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly expandedId = signal<number | null>(null);
  readonly activeIcao = signal<string | null>(null);

  icaoFilter = '';
  private readonly pageSize = 20;

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  applyFilter(): void {
    const icao = this.icaoFilter.trim().toUpperCase();
    this.activeIcao.set(icao || null);
    this.page.set(0);
    this.expandedId.set(null);
    this.load();
  }

  clearFilter(): void {
    this.icaoFilter = '';
    this.activeIcao.set(null);
    this.page.set(0);
    this.expandedId.set(null);
    this.load();
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

  toggle(id: number): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  displayCallsign(a: AnomalyDto): string {
    return a.callsign?.trim() ? a.callsign : 'UNKNOWN';
  }

  severityClass(score: number): string {
    if (score >= 7) return 'sev-crit';
    if (score >= 4) return 'sev-high';
    if (score >= 2) return 'sev-med';
    return 'sev-low';
  }

  timeAgo(iso: string): string {
    const then = new Date(iso).getTime();
    const secs = Math.max(0, Math.floor((Date.now() - then) / 1000));
    if (secs < 60) return 'just now';
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.sub?.unsubscribe();
    this.sub = this.api
      .getPublicAnomalies(this.activeIcao() ?? undefined, this.page(), this.pageSize)
      .subscribe({
        next: (resp) => {
          this.anomalies.set(resp.content);
          this.totalPages.set(resp.totalPages);
          this.totalElements.set(resp.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not load anomalies. The backend may be offline.');
          this.loading.set(false);
        },
      });
  }
}

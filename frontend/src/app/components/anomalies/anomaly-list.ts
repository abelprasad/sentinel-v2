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
            <li [class]="cardClass(a)">
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
    :host { display: block; animation: sn-fade-up var(--dur-med) var(--ease-out); }
    h2 { font-size: 1.25rem; margin: 0 0 1rem; font-weight: 700; letter-spacing: -0.01em; }
    .filter { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .filter label { font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.08em; color: var(--text-3); }
    .filter input { background: var(--s0); border: 1px solid var(--border); color: var(--text); padding: 0.45rem 0.65rem; border-radius: var(--r-md); width: 9rem; text-transform: uppercase; font-family: var(--font-mono); font-size: 0.85rem; transition: border-color var(--dur-fast), box-shadow var(--dur-fast); }
    .filter input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgb(34 211 238 / 0.15); }
    .filter button { background: var(--bg-elevated); border: 1px solid var(--border); color: var(--text-2); padding: 0.45rem 0.9rem; border-radius: var(--r-md); cursor: pointer; font-weight: 600; font-size: 0.85rem; transition: all var(--dur-fast) var(--ease-out); }
    .filter button:hover { border-color: var(--accent-border); color: var(--accent); background: var(--accent-soft); }
    .status { color: var(--text-3); padding: 2rem 0; text-align: center; }
    .status.error { color: var(--crit-400); }
    .anomaly-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }
    .anomaly-card { border: 1px solid var(--border); border-left: 3px solid var(--warn-400); border-radius: var(--r-md); overflow: hidden; background: linear-gradient(180deg, var(--bg-card), var(--bg-panel)); transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast), border-color var(--dur-fast); }
    .anomaly-card:hover { transform: translateX(3px); box-shadow: var(--sh-card); border-color: var(--b-strong); }
    .anomaly-card.acknowledged { opacity: 0.62; }
    .anomaly-card.sev-crit { border-left-color: var(--crit-400); }
    .anomaly-card.sev-high { border-left-color: var(--warn-400); }
    .anomaly-card.sev-med { border-left-color: var(--warn-400); opacity: 0.92; }
    .anomaly-card.sev-low { border-left-color: var(--info-400); }
    .row-toggle { width: 100%; display: flex; align-items: center; gap: 0.9rem; padding: 0.75rem 0.95rem; background: none; border: none; color: inherit; cursor: pointer; text-align: left; font-family: inherit; }
    .row-toggle:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
    .score-badge { min-width: 3.4rem; text-align: center; font-weight: 800; font-family: var(--font-mono); font-variant-numeric: tabular-nums; padding: 0.4rem 0.5rem; border-radius: var(--r-md); font-size: 0.95rem; border: 1px solid transparent; }
    .sev-low { background: var(--info-bg); color: var(--info-400); border-color: rgb(96 165 250 / 0.3); }
    .sev-med { background: var(--warn-bg); color: var(--warn-400); border-color: rgb(251 191 36 / 0.3); }
    .sev-high { background: var(--warn-bg); color: var(--warn-400); border-color: rgb(251 191 36 / 0.45); box-shadow: 0 0 12px rgb(251 191 36 / 0.18); }
    .sev-crit { background: var(--crit-bg); color: var(--crit-400); border-color: rgb(248 113 113 / 0.45); box-shadow: var(--glow-crit); animation: sn-blink 2.4s ease-in-out infinite; }
    .main { display: flex; flex-direction: column; flex: 1; min-width: 0; gap: 0.1rem; }
    .callsign { font-weight: 700; color: var(--text); font-family: var(--font-mono); font-size: 0.92rem; letter-spacing: 0.02em; }
    .icao { font-size: 0.72rem; color: var(--text-4); font-family: var(--font-mono); }
    .meta { display: flex; align-items: center; gap: 0.5rem; font-size: 0.8rem; color: var(--text-3); font-family: var(--font-mono); }
    .pill { font-size: 0.64rem; font-weight: 700; letter-spacing: 0.07em; padding: 0.18rem 0.5rem; border-radius: var(--r-full); border: 1px solid transparent; }
    .pill.ack { background: rgb(148 184 220 / 0.1); color: var(--text-3); border-color: var(--border); }
    .pill.esc { background: var(--crit-bg); color: var(--crit-400); border-color: rgb(248 113 113 / 0.4); }
    .chevron { color: var(--text-4); font-size: 0.8rem; transition: transform var(--dur-fast); }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1.2rem; font-size: 0.85rem; color: var(--text-3); font-family: var(--font-mono); }
    .pagination button { background: var(--bg-elevated); border: 1px solid var(--border); color: var(--text-2); padding: 0.45rem 1rem; border-radius: var(--r-md); cursor: pointer; font-weight: 600; transition: all var(--dur-fast); }
    .pagination button:hover:not(:disabled) { border-color: var(--accent-border); color: var(--accent); }
    .pagination button:disabled { opacity: 0.35; cursor: default; }

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

  /** Full CSS class string for an anomaly card (severity + acknowledged). */
  cardClass(a: AnomalyDto): string {
    return `anomaly-card ${this.severityClass(a.score)}${a.acknowledged ? ' acknowledged' : ''}`;
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

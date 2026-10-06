import { Component, input, computed } from '@angular/core';

import { AnomalyDto } from '../../core/models/api.models';

/**
 * Expandable anomaly detail panel.
 *
 * Shows the per-dimension z-score breakdown as bars, the human-readable
 * explanation (prominently), the explanation source badge (RULE vs AI),
 * and the escalation-thread link when the anomaly is a child of an
 * existing incident.
 *
 * Public component: no auth imports, no token reads.
 */
@Component({
  selector: 'app-anomaly-detail',
  standalone: true,
  template: `
    <div class="detail" role="region" aria-label="Anomaly detail">
      <div class="detail-grid">
        <!-- Z-score breakdown -->
        <section class="zscores" aria-labelledby="zscore-heading">
          <h4 id="zscore-heading">Signal breakdown</h4>
          @for (dim of dimensions(); track dim.key) {
            <div class="zrow" [attr.aria-label]="dim.label + ' z-score ' + formatZ(dim.value)">
              <span class="zlabel">{{ dim.label }}</span>
              <div class="zbar-track">
                <div
                  class="zbar"
                  [style.width.%]="barWidth(dim.value)"
                  [class.hot]="isHot(dim.value)"
                ></div>
              </div>
              <span class="zvalue">{{ formatZ(dim.value) }}</span>
            </div>
          }
          <p class="zlegend">Z-scores measure deviation from the aircraft baseline. |z| &ge; 3 is significant.</p>
        </section>

        <!-- Explanation -->
        <section class="explanation" aria-labelledby="expl-heading">
          <h4 id="expl-heading">
            Why this was flagged
            @if (anomaly().explanationSrc) {
              <span
                class="src-badge"
                [class.ai]="anomaly().explanationSrc === 'llm'"
                [class.rule]="anomaly().explanationSrc === 'rule'"
              >
                {{ anomaly().explanationSrc === 'llm' ? 'AI' : 'RULE' }}
              </span>
            }
          </h4>
          <p class="expl-text">
            {{ anomaly().explanation ?? 'No explanation recorded for this anomaly.' }}
          </p>

          @if (anomaly().parentAnomalyId != null) {
            <p class="thread">
              Part of incident thread
              <a href="/map" [attr.aria-label]="'View incident thread ' + anomaly().parentAnomalyId">
                #{{ anomaly().parentAnomalyId }}
              </a>
              &mdash; this flag escalated from an earlier detection.
            </p>
          }
        </section>
      </div>
    </div>
  `,
  styles: [
    `
    .detail { padding: 1.1rem 1.25rem; background: rgb(4 6 12 / 0.5); border-top: 1px solid var(--border); }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    @media (max-width: 640px) { .detail-grid { grid-template-columns: 1fr; } }
    h4 { margin: 0 0 0.8rem; font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.12em; color: var(--text-3); display: flex; align-items: center; gap: 0.5rem; }
    .zrow { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.5rem; }
    .zlabel { width: 5.5rem; font-size: 0.8rem; font-weight: 600; color: var(--text-2); }
    .zbar-track { flex: 1; height: 0.55rem; background: var(--s0); border: 1px solid var(--b-subtle); border-radius: var(--r-full); overflow: hidden; }
    .zbar { height: 100%; background: linear-gradient(90deg, var(--cyan-500), var(--accent)); border-radius: var(--r-full); transition: width 0.3s var(--ease-out); box-shadow: 0 0 8px rgb(34 211 238 / 0.35); }
    .zbar.hot { background: linear-gradient(90deg, var(--warn-400), var(--crit-400)); box-shadow: 0 0 10px rgb(248 113 113 / 0.5); }
    .zvalue { width: 3.8rem; text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 0.85rem; font-weight: 600; color: var(--text); }
    .zlegend { font-size: 0.72rem; color: var(--text-4); margin: 0.7rem 0 0; }
    .expl-text { font-size: 0.92rem; line-height: 1.6; color: var(--text); margin: 0; border-left: 3px solid var(--accent); padding: 0.2rem 0 0.2rem 0.85rem; text-shadow: 0 1px 8px rgb(0 0 0 / 0.4); }
    .src-badge { font-size: 0.64rem; font-weight: 800; letter-spacing: 0.08em; padding: 0.2rem 0.55rem; border-radius: var(--r-full); border: 1px solid transparent; }
    .src-badge.rule { background: var(--info-bg); color: var(--info-400); border-color: rgb(96 165 250 / 0.35); }
    .src-badge.ai { background: var(--violet-bg); color: var(--violet-400); border-color: rgb(167 139 250 / 0.4); box-shadow: 0 0 10px rgb(167 139 250 / 0.2); }
    .thread { margin-top: 0.9rem; font-size: 0.85rem; color: var(--text-3); }
    .thread a { color: var(--accent); font-weight: 600; font-family: var(--font-mono); text-decoration: none; border-bottom: 1px dotted var(--accent-border); }
    .thread a:hover { border-bottom-style: solid; }

    `,
  ],
})
export class AnomalyDetailComponent {
  readonly anomaly = input.required<AnomalyDto>();

  /** The four detection dimensions, in display order. */
  readonly dimensions = computed(() => {
    const a = this.anomaly();
    return [
      { key: 'altitude', label: 'Altitude', value: a.zAltitude },
      { key: 'speed', label: 'Speed', value: a.zSpeed },
      { key: 'heading', label: 'Heading', value: a.zHeading },
      { key: 'position', label: 'Position', value: a.zPosition },
    ] as const;
  });

  formatZ(z: number | null): string {
    if (z == null) return 'n/a';
    const signed = z >= 0 ? '+' : '';
    return `${signed}${z.toFixed(2)}`;
  }

  /** Bar width scaled so |z| = 6 fills the track. */
  barWidth(z: number | null): number {
    if (z == null) return 0;
    return Math.min(100, (Math.abs(z) / 6) * 100);
  }

  isHot(z: number | null): boolean {
    return z != null && Math.abs(z) >= 3;
  }
}

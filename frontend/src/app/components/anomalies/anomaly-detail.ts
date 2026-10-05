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
    .detail { padding: 1rem 1.25rem; background: #0d1520; border-top: 1px solid #1e2a3a; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    @media (max-width: 640px) { .detail-grid { grid-template-columns: 1fr; } }
    h4 { margin: 0 0 0.75rem; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.08em; color: #8fa3bd; display: flex; align-items: center; gap: 0.5rem; }
    .zrow { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.45rem; }
    .zlabel { width: 5.5rem; font-size: 0.8rem; color: #c7d3e3; }
    .zbar-track { flex: 1; height: 0.6rem; background: #182233; border-radius: 0.3rem; overflow: hidden; }
    .zbar { height: 100%; background: #4aa8ff; border-radius: 0.3rem; transition: width 0.2s ease; }
    .zbar.hot { background: #ff5a5a; }
    .zvalue { width: 3.5rem; text-align: right; font-variant-numeric: tabular-nums; font-size: 0.85rem; color: #dbe5f2; }
    .zlegend { font-size: 0.72rem; color: #66788f; margin: 0.6rem 0 0; }
    .expl-text { font-size: 0.95rem; line-height: 1.55; color: #eef3fa; margin: 0; border-left: 3px solid #4aa8ff; padding-left: 0.75rem; }
    .src-badge { font-size: 0.68rem; font-weight: 700; letter-spacing: 0.06em; padding: 0.15rem 0.5rem; border-radius: 0.25rem; }
    .src-badge.rule { background: #1d3a5f; color: #8fc2ff; }
    .src-badge.ai { background: #3d2b5f; color: #c9a6ff; }
    .thread { margin-top: 0.9rem; font-size: 0.85rem; color: #a9bad1; }
    .thread a { color: #6db4ff; font-weight: 600; }
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

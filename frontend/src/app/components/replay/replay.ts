import { Component, signal, computed, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/**
 * Replay mode: deterministic scripted scenario player for the SENTINEL demo.
 *
 * PURE FRONTEND — makes ZERO backend calls. All scenarios are hardcoded
 * synthetic track sequences with scripted anomaly flags, so this works
 * fully offline (the interview demo artifact).
 *
 * Public component: no auth imports, no token reads, no ApiService.
 */

interface ReplayAnomaly {
  score: number;
  zAltitude: number | null;
  zSpeed: number | null;
  zHeading: number | null;
  zPosition: number | null;
  explanation: string;
  explanationSrc: 'rule' | 'llm';
  escalated: boolean;
  parentAnomalyId: number | null;
}

interface ReplayPoint {
  /** Seconds since scenario start. */
  t: number;
  lat: number;
  lon: number;
  altFt: number;
  spdKts: number;
  hdgDeg: number;
  anomaly?: ReplayAnomaly;
}

interface ReplayScenario {
  id: string;
  name: string;
  tagline: string;
  description: string;
  icaoHex: string;
  callsign: string;
  points: ReplayPoint[];
}

interface LoggedAnomaly extends ReplayAnomaly {
  t: number;
}

interface PlotPoint {
  x: number;
  y: number;
  anomaly: boolean;
}

const STEP = 4; // seconds between synthetic points

function lerp(a: number, b: number, f: number): number {
  return a + (b - a) * f;
}

/** Deterministic point builder: base path + optional anomaly flags at indices. */
function buildPoints(
  count: number,
  path: (i: number) => Omit<ReplayPoint, 't'>,
  anomalies: Record<number, ReplayAnomaly>,
): ReplayPoint[] {
  const pts: ReplayPoint[] = [];
  for (let i = 0; i < count; i++) {
    const p = path(i);
    pts.push({ ...p, t: i * STEP, anomaly: anomalies[i] });
  }
  return pts;
}

const SCENARIOS: ReplayScenario[] = [
  {
    id: 'gps-spoof',
    name: 'GPS spoofing event',
    tagline: 'Position jumps 70 nm off-track, then snaps back',
    description:
      'A business jet on a steady eastbound airway suddenly reports a position 70 nautical miles south of its track, then jumps back two minutes later. Classic GPS spoofing signature.',
    icaoHex: 'A1B2C3',
    callsign: 'N123AB',
    points: buildPoints(
      36,
      (i) => {
        const spoofed = i >= 14 && i < 24;
        return {
          lat: 40.2 + i * 0.004 - (spoofed ? 1.1 : 0),
          lon: -75.8 + i * 0.03,
          altFt: 41000,
          spdKts: 445,
          hdgDeg: 90,
        };
      },
      {
        14: {
          score: 8.6,
          zAltitude: 0.4,
          zSpeed: 0.9,
          zHeading: 0.2,
          zPosition: 9.4,
          explanation:
            'Reported position is 70 nm from the projected track and inconsistent with groundspeed. Position z-score 9.4 exceeds the spoofing threshold.',
          explanationSrc: 'rule',
          escalated: true,
          parentAnomalyId: 1042,
        },
        24: {
          score: 7.9,
          zAltitude: 0.3,
          zSpeed: 0.5,
          zHeading: 0.1,
          zPosition: 8.1,
          explanation:
            'Position snapped back onto the projected track after 40 seconds of spoofed fixes. Matches known GPS interference patterns for this sector.',
          explanationSrc: 'rule',
          escalated: true,
          parentAnomalyId: 1042,
        },
      },
    ),
  },
  {
    id: 'alt-deviation',
    name: 'Altitude deviation',
    tagline: 'Unexplained descent 2,500 ft below assigned altitude',
    description:
      'A regional jet cleared to 24,000 ft begins an unexplained descent to 21,500 ft with no clearance change on record. Altitude z-score climbs past the significant threshold.',
    icaoHex: 'D4E5F6',
    callsign: 'SKW4421',
    points: buildPoints(
      36,
      (i) => ({
        lat: 39.9 + i * 0.003,
        lon: -77.2 + i * 0.022,
        altFt: i < 10 ? 24000 : i < 22 ? 24000 - (i - 10) * 210 : 21500,
        spdKts: 320,
        hdgDeg: 82,
      }),
      {
        16: {
          score: 7.2,
          zAltitude: -4.8,
          zSpeed: 0.6,
          zHeading: 0.3,
          zPosition: 0.4,
          explanation:
            'Altitude 1,260 ft below assigned level and still descending. No clearance amendment in the data. Rate of descent is abnormal for this phase of flight.',
          explanationSrc: 'llm',
          escalated: false,
          parentAnomalyId: null,
        },
        23: {
          score: 8.9,
          zAltitude: -6.1,
          zSpeed: 1.1,
          zHeading: 0.4,
          zPosition: 0.7,
          explanation:
            'Deviation now 2,500 ft below assigned altitude with no recovery. Escalated: possible loss of separation risk with traffic below.',
          explanationSrc: 'llm',
          escalated: true,
          parentAnomalyId: null,
        },
      },
    ),
  },
  {
    id: 'holding',
    name: 'Unusual holding pattern',
    tagline: 'Uncharted racetrack orbits over suburban airspace',
    description:
      'A turboprop enters a tight racetrack hold over suburban airspace where no published hold exists. Heading z-score spikes as the aircraft orbits twice.',
    icaoHex: '7C8D9E',
    callsign: 'FDX2214',
    points: buildPoints(
      48,
      (i) => {
        const hold = i >= 12 && i < 36;
        const a = hold ? (i - 12) * 0.52 : 0;
        return {
          lat: 40.0 + i * 0.0035 + (hold ? Math.sin(a) * 0.045 : 0),
          lon: -76.5 + i * 0.02 + (hold ? Math.cos(a) * 0.06 : 0),
          altFt: 12000,
          spdKts: 210,
          hdgDeg: hold ? Math.round(((Math.atan2(Math.cos(a), -Math.sin(a)) * 180) / Math.PI + 360) % 360) : 80,
        };
      },
      {
        15: {
          score: 6.4,
          zAltitude: 0.2,
          zSpeed: 0.8,
          zHeading: 5.7,
          zPosition: 1.9,
          explanation:
            'Track geometry matches a holding pattern, but no published hold exists at this location. Heading variance 5.7 sigma above baseline.',
          explanationSrc: 'rule',
          escalated: false,
          parentAnomalyId: null,
        },
      },
    ),
  },
  {
    id: 'speed-excursion',
    name: 'Speed excursion',
    tagline: 'Acceleration past the aircraft envelope at low altitude',
    description:
      'A light aircraft accelerates past 250 kts below 10,000 ft, breaching the regulatory speed limit and its own baseline envelope. Speed z-score peaks at 7.1.',
    icaoHex: 'B3C4D5',
    callsign: 'N77TP',
    points: buildPoints(
      32,
      (i) => ({
        lat: 40.3 + i * 0.005,
        lon: -75.2 + i * 0.028,
        altFt: 8500,
        spdKts: i < 8 ? 165 : i < 20 ? 165 + (i - 8) * 9 : 273 - (i - 20) * 2,
        hdgDeg: 78,
      }),
      {
        13: {
          score: 5.8,
          zAltitude: 0.5,
          zSpeed: 4.2,
          zHeading: 0.2,
          zPosition: 0.6,
          explanation:
            'Groundspeed rising through the baseline envelope with no descent to explain the acceleration.',
          explanationSrc: 'rule',
          escalated: false,
          parentAnomalyId: null,
        },
        19: {
          score: 8.1,
          zAltitude: 0.9,
          zSpeed: 7.1,
          zHeading: 0.3,
          zPosition: 1.1,
          explanation:
            'Speed now 273 kts below 10,000 ft: 7.1 sigma above baseline and past the 250-kt regulatory limit. Sustained for over a minute.',
          explanationSrc: 'llm',
          escalated: true,
          parentAnomalyId: null,
        },
      },
    ),
  },
];

const VIEW_W = 560;
const VIEW_H = 260;
const PAD = 24;

@Component({
  selector: 'app-replay',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section aria-labelledby="replay-heading">
      <div class="replay-head">
        <div>
          <h2 id="replay-heading">Replay mode</h2>
          <p class="intro">
            Deterministic scripted scenarios. No backend, no network — everything you see is
            synthesized locally so this demo works fully offline.
          </p>
        </div>
        <span class="badge badge-accent offline-badge">100% offline</span>
      </div>

      <div class="scenario-picker" role="group" aria-label="Choose a scenario">
        @for (s of scenarios; track s.id) {
          <button
            type="button"
            class="scenario-btn"
            [class.active]="s.id === activeId()"
            (click)="selectScenario(s.id)"
            [attr.aria-pressed]="s.id === activeId()"
          >
            <span class="scenario-name">{{ s.name }}</span>
            <span class="scenario-tagline">{{ s.tagline }}</span>
          </button>
        }
      </div>

      <article class="player" [attr.aria-label]="'Replay of ' + scenario().name">
        <header>
          <div>
            <h3>{{ scenario().name }}</h3>
            <p class="callsign-line">
              {{ scenario().callsign }} &middot; ICAO {{ scenario().icaoHex }}
            </p>
          </div>
          <p class="elapsed" aria-live="polite">T+{{ clock().toFixed(0) }}s / {{ duration() }}s</p>
        </header>

        <p class="description">{{ scenario().description }}</p>

        <svg
          class="track-plot"
          [attr.viewBox]="'0 0 ' + VIEW_W + ' ' + VIEW_H"
          role="img"
          [attr.aria-label]="'Flight track for ' + scenario().name"
        >
          <polyline
            [attr.points]="pathPoints()"
            fill="none"
            stroke="#22314d"
            stroke-width="2"
          />
          @for (p of anomalyMarkers(); track p.t) {
            <circle [attr.cx]="p.x" [attr.cy]="p.y" r="6" fill="none" stroke="#f87171" stroke-width="2">
              <title>Anomaly flagged at T+{{ p.t }}s</title>
            </circle>
          }
          <circle [attr.cx]="position().x" [attr.cy]="position().y" r="7" fill="#22d3ee" />
          <circle [attr.cx]="position().x" [attr.cy]="position().y" r="11" fill="none" stroke="#22d3ee" stroke-opacity="0.4" />
        </svg>

        <div class="telemetry" aria-label="Current telemetry" aria-live="polite">
          <div><span>Lat</span><strong>{{ current().lat.toFixed(3) }}&deg;</strong></div>
          <div><span>Lon</span><strong>{{ current().lon.toFixed(3) }}&deg;</strong></div>
          <div><span>Alt</span><strong>{{ current().altFt | number:'1.0-0' }} ft</strong></div>
          <div><span>Spd</span><strong>{{ current().spdKts | number:'1.0-0' }} kts</strong></div>
          <div><span>Hdg</span><strong>{{ current().hdgDeg }}&deg;</strong></div>
        </div>

        <div class="controls">
          <button type="button" (click)="togglePlay()" [attr.aria-label]="playing() ? 'Pause replay' : 'Play replay'">
            {{ playing() ? '&#10074;&#10074; Pause' : '&#9654; Play' }}
          </button>
          <button type="button" (click)="reset()" aria-label="Reset replay">&#8634; Reset</button>
          <label class="speed">
            Speed
            <select [(ngModel)]="speed" aria-label="Playback speed" (change)="onSpeedChange()">
              @for (opt of speeds; track opt) {
                <option [value]="opt">{{ opt }}x</option>
              }
            </select>
          </label>
          <input
            type="range"
            class="scrubber"
            [min]="0"
            [max]="duration()"
            [step]="1"
            [value]="clock()"
            (input)="scrub($event)"
            aria-label="Scrub replay timeline"
          />
        </div>

        <h4>Detection log</h4>
        @if (log().length === 0) {
          <p class="no-events">No anomalies flagged yet — press play.</p>
        } @else {
          <ul class="event-log">
            @for (e of log(); track e.t) {
              <li class="event" [class.escalated]="e.escalated">
                <span class="event-t">T+{{ e.t }}s</span>
                <span class="event-score" title="Anomaly score">{{ e.score.toFixed(1) }}</span>
                <div class="event-body">
                  <p>{{ e.explanation }}</p>
                  <span class="src-badge" [class.ai]="e.explanationSrc === 'llm'">
                    {{ e.explanationSrc === 'llm' ? 'AI' : 'RULE' }}
                  </span>
                  @if (e.escalated) {
                    <span class="esc-pill">ESCALATED</span>
                  }
                  @if (e.parentAnomalyId != null) {
                    <span class="thread-note">thread #{{ e.parentAnomalyId }}</span>
                  }
                </div>
              </li>
            }
          </ul>
        }
      </article>
    </section>
  `,
  styles: [
    `
    :host { display: block; max-width: 76rem; margin: 0 auto; padding: 2rem 1.5rem 3rem; animation: sn-fade-up var(--dur-med) var(--ease-out); }
    .replay-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
    h2 { font-size: 1.9rem; margin: 0; font-weight: 800; letter-spacing: -0.02em; background: linear-gradient(90deg, #fff, var(--cyan-300)); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .intro { color: var(--text-3); font-size: 0.95rem; margin: 0.4rem 0 0; max-width: 44rem; line-height: 1.55; }
    .offline-badge { flex: none; }
    .scenario-picker { display: grid; grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr)); gap: 0.7rem; margin-bottom: 1.2rem; }
    .scenario-btn { display: flex; flex-direction: column; gap: 0.25rem; text-align: left; background: linear-gradient(180deg, var(--bg-card), var(--bg-panel)); border: 1px solid var(--border); color: var(--text); padding: 0.85rem 1rem; border-radius: var(--r-lg); cursor: pointer; transition: all var(--dur-fast) var(--ease-out); }
    .scenario-btn:hover { transform: translateY(-2px); border-color: var(--b-strong); box-shadow: var(--sh-card); }
    .scenario-btn.active { border-color: var(--accent-border); background: linear-gradient(180deg, rgb(34 211 238 / 0.1), var(--bg-card)); box-shadow: var(--glow-accent); }
    .scenario-name { font-weight: 700; font-size: 0.95rem; }
    .scenario-tagline { font-size: 0.78rem; color: var(--text-3); line-height: 1.45; }
    .player { background: linear-gradient(180deg, var(--bg-panel), var(--bg-shell)); border: 1px solid var(--b-strong); border-radius: var(--r-xl); padding: 1.5rem; box-shadow: var(--sh-pop); }
    .player header { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; flex-wrap: wrap; }
    .player h3 { margin: 0; font-size: 1.25rem; font-weight: 800; font-family: var(--font-mono); letter-spacing: 0.02em; }
    .callsign-line { margin: 0.25rem 0 0; font-size: 0.78rem; color: var(--text-3); font-family: var(--font-mono); letter-spacing: 0.06em; }
    .elapsed { font-variant-numeric: tabular-nums; font-size: 1rem; color: var(--accent); margin: 0; font-family: var(--font-mono); font-weight: 600; text-shadow: 0 0 12px var(--cyan-glow); }
    .description { color: var(--text-2); font-size: 0.92rem; line-height: 1.6; max-width: 48rem; }
    .track-plot { width: 100%; height: auto; background: var(--s0); border: 1px solid var(--border); border-radius: var(--r-lg); margin: 0.9rem 0; box-shadow: inset 0 0 50px rgb(0 0 0 / 0.5); }
    .telemetry { display: grid; grid-template-columns: repeat(5, 1fr); gap: 0.6rem; margin-bottom: 1.2rem; }
    .telemetry div { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--r-md); padding: 0.6rem 0.8rem; display: flex; flex-direction: column; gap: 0.15rem; transition: border-color var(--dur-fast); }
    .telemetry div:hover { border-color: var(--b-strong); }
    .telemetry span { font-size: 0.64rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.1em; color: var(--text-4); }
    .telemetry strong { font-size: 1.05rem; font-family: var(--font-mono); font-variant-numeric: tabular-nums; color: var(--text); font-weight: 700; }
    @media (max-width: 640px) { .telemetry { grid-template-columns: repeat(2, 1fr); } }
    .controls { display: flex; align-items: center; gap: 0.8rem; flex-wrap: wrap; margin-bottom: 1.2rem; padding: 0.8rem 1rem; background: rgb(4 6 12 / 0.5); border: 1px solid var(--b-subtle); border-radius: var(--r-lg); }
    .controls button { background: var(--bg-elevated); border: 1px solid var(--border); color: var(--text); padding: 0.5rem 1.1rem; border-radius: var(--r-md); cursor: pointer; font-size: 0.88rem; font-weight: 700; transition: all var(--dur-fast) var(--ease-out); }
    .controls button:hover { border-color: var(--accent-border); color: var(--accent); box-shadow: var(--glow-accent); }
    .speed { display: flex; align-items: center; gap: 0.45rem; font-size: 0.85rem; color: var(--text-3); }
    .speed select { background: var(--s0); border: 1px solid var(--border); color: var(--text); padding: 0.4rem 0.55rem; border-radius: var(--r-md); font-family: var(--font-mono); }
    .scrubber { flex: 1; min-width: 8rem; accent-color: var(--accent); }
    .player h4 { margin: 0 0 0.7rem; font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.12em; color: var(--text-3); }
    .no-events { color: var(--text-4); font-size: 0.88rem; padding: 1rem; text-align: center; border: 1px dashed var(--b-strong); border-radius: var(--r-md); }
    .event-log { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }
    .event { display: flex; gap: 0.9rem; align-items: flex-start; background: var(--bg-card); border: 1px solid var(--border); border-left: 3px solid var(--warn-400); border-radius: var(--r-md); padding: 0.8rem 1rem; animation: sn-fade-up var(--dur-med) var(--ease-out); }
    .event.escalated { border-left-color: var(--crit-400); box-shadow: var(--glow-crit); }
    .event-t { font-family: var(--font-mono); font-size: 0.8rem; color: var(--text-3); white-space: nowrap; font-variant-numeric: tabular-nums; }
    .event-score { font-weight: 800; font-family: var(--font-mono); font-variant-numeric: tabular-nums; color: var(--warn-400); white-space: nowrap; font-size: 0.95rem; }
    .event.escalated .event-score { color: var(--crit-400); }
    .event-body p { margin: 0 0 0.45rem; font-size: 0.9rem; line-height: 1.55; color: var(--text); }
    .src-badge { font-size: 0.62rem; font-weight: 800; letter-spacing: 0.08em; padding: 0.16rem 0.5rem; border-radius: var(--r-full); background: var(--info-bg); color: var(--info-400); border: 1px solid rgb(96 165 250 / 0.35); margin-right: 0.4rem; }
    .src-badge.ai { background: var(--violet-bg); color: var(--violet-400); border-color: rgb(167 139 250 / 0.4); }
    .esc-pill { font-size: 0.62rem; font-weight: 800; letter-spacing: 0.06em; padding: 0.16rem 0.5rem; border-radius: var(--r-full); background: var(--crit-bg); color: var(--crit-400); border: 1px solid rgb(248 113 113 / 0.4); margin-right: 0.4rem; }
    .thread-note { font-size: 0.75rem; color: var(--text-4); }

    `,
  ],
})
export class ReplayComponent implements OnDestroy {
  readonly scenarios = SCENARIOS;
  readonly speeds = [1, 2, 4];
  readonly VIEW_W = VIEW_W;
  readonly VIEW_H = VIEW_H;

  readonly activeId = signal(SCENARIOS[0].id);
  readonly clock = signal(0);
  readonly playing = signal(false);
  speed = 1;

  private timer: ReturnType<typeof setInterval> | null = null;
  private boundsCache: Record<string, { minLat: number; maxLat: number; minLon: number; maxLon: number }> = {};

  readonly scenario = computed(() => this.scenarios.find((s) => s.id === this.activeId())!);
  readonly duration = computed(() => this.scenario().points[this.scenario().points.length - 1].t);

  /** All scripted anomalies whose flag time has been reached — deterministic from clock. */
  readonly log = computed<LoggedAnomaly[]>(() =>
    this.scenario()
      .points.filter((p) => p.t <= this.clock() && p.anomaly)
      .map((p) => ({ t: p.t, ...p.anomaly! })),
  );

  /** Interpolated aircraft state at the current clock. */
  readonly current = computed(() => this.stateAt(this.clock()));

  /** Aircraft position projected to the SVG viewBox. */
  readonly position = computed(() => this.project(this.current().lat, this.current().lon));

  readonly pathPoints = computed(() =>
    this.scenario()
      .points.map((p) => {
        const q = this.project(p.lat, p.lon);
        return `${q.x.toFixed(1)},${q.y.toFixed(1)}`;
      })
      .join(' '),
  );

  readonly anomalyMarkers = computed(() =>
    this.scenario()
      .points.filter((p) => p.anomaly && p.t <= this.clock())
      .map((p) => ({ t: p.t, ...this.project(p.lat, p.lon) })),
  );

  ngOnDestroy(): void {
    this.stopTimer();
  }

  selectScenario(id: string): void {
    this.stopTimer();
    this.activeId.set(id);
    this.clock.set(0);
    this.playing.set(false);
  }

  togglePlay(): void {
    if (this.playing()) {
      this.stopTimer();
      this.playing.set(false);
      return;
    }
    if (this.clock() >= this.duration()) {
      this.clock.set(0);
    }
    this.playing.set(true);
    // 100ms real tick advances clock by speed * 0.1 sim-seconds.
    this.timer = setInterval(() => {
      const next = this.clock() + this.speed * 0.1;
      if (next >= this.duration()) {
        this.clock.set(this.duration());
        this.stopTimer();
        this.playing.set(false);
      } else {
        this.clock.set(next);
      }
    }, 100);
  }

  onSpeedChange(): void {
    // If playing, restart the timer so the new speed applies immediately.
    if (this.playing()) {
      this.stopTimer();
      this.playing.set(false);
      this.togglePlay();
    }
  }

  reset(): void {
    this.stopTimer();
    this.clock.set(0);
    this.playing.set(false);
  }

  scrub(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.stopTimer();
    this.playing.set(false);
    this.clock.set(Math.min(Math.max(value, 0), this.duration()));
  }

  private stopTimer(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private stateAt(t: number): ReplayPoint {
    const pts = this.scenario().points;
    if (t <= pts[0].t) return pts[0];
    const last = pts[pts.length - 1];
    if (t >= last.t) return last;
    for (let i = 1; i < pts.length; i++) {
      if (pts[i].t >= t) {
        const a = pts[i - 1];
        const b = pts[i];
        const f = (t - a.t) / (b.t - a.t);
        return {
          t,
          lat: lerp(a.lat, b.lat, f),
          lon: lerp(a.lon, b.lon, f),
          altFt: lerp(a.altFt, b.altFt, f),
          spdKts: lerp(a.spdKts, b.spdKts, f),
          hdgDeg: Math.round(lerp(a.hdgDeg, b.hdgDeg, f)),
        };
      }
    }
    return last;
  }

  private bounds(): { minLat: number; maxLat: number; minLon: number; maxLon: number } {
    const id = this.scenario().id;
    let b = this.boundsCache[id];
    if (!b) {
      const lats = this.scenario().points.map((p) => p.lat);
      const lons = this.scenario().points.map((p) => p.lon);
      b = {
        minLat: Math.min(...lats),
        maxLat: Math.max(...lats),
        minLon: Math.min(...lons),
        maxLon: Math.max(...lons),
      };
      this.boundsCache[id] = b;
    }
    return b;
  }

  private project(lat: number, lon: number): PlotPoint {
    const b = this.bounds();
    const lonSpan = Math.max(0.0001, b.maxLon - b.minLon);
    const latSpan = Math.max(0.0001, b.maxLat - b.minLat);
    return {
      x: PAD + ((lon - b.minLon) / lonSpan) * (VIEW_W - 2 * PAD),
      y: PAD + ((b.maxLat - lat) / latSpan) * (VIEW_H - 2 * PAD),
      anomaly: false,
    };
  }
}

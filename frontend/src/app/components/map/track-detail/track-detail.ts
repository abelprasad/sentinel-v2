import {
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, catchError, of, switchMap, timer } from 'rxjs';
import * as L from 'leaflet';

import { ApiService } from '../../../core/api.service';
import { TrackDto, TrackPointDto } from '../../../core/models/api.models';
import { MAP_CENTER, MAP_REFRESH_INTERVAL_MS, TILE_LAYER } from '../map.config';

type PlottablePoint = TrackPointDto & { lat: number; lon: number };

/**
 * Flight track detail panel for the public map.
 *
 * Loads the selected aircraft's track via the public API, draws the flight
 * path as a polyline on a small Leaflet map, and lists recent telemetry
 * (altitude, speed, heading, time) in a table. Re-polls every 30s while
 * open, matching the backend cadence. No auth involved.
 *
 * State is signal-based: the app is zoneless, so async updates must go
 * through signals to reach the template.
 */
@Component({
  selector: 'app-track-detail',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './track-detail.html',
  styleUrl: './track-detail.scss',
})
export class TrackDetailComponent implements OnDestroy {
  /** ICAO hex of the aircraft to show. Null hides the panel. */
  readonly icaoHex = input<string | null>(null);
  /** Emitted when the user closes the panel. */
  readonly closed = output<void>();

  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly detailMapEl = viewChild<ElementRef<HTMLDivElement>>('detailMap');

  protected readonly track = signal<TrackDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  private map: L.Map | null = null;
  private trackLayer: L.LayerGroup | null = null;
  private poll: Subscription | null = null;

  constructor() {
    effect(() => {
      this.watch(this.icaoHex());
    });
  }

  ngOnDestroy(): void {
    this.stopWatching();
  }

  /** Close button handler. Public for template and tests. */
  close(): void {
    this.closed.emit();
  }

  /** Newest-first telemetry rows for the table (latest 10 points). */
  protected recentPoints(): TrackPointDto[] {
    return (this.track()?.points ?? []).slice(-10).reverse();
  }

  private watch(icao: string | null): void {
    this.stopWatching();
    this.track.set(null);
    this.error.set(null);
    this.loading.set(false);
    if (!icao) {
      return;
    }
    this.loading.set(true);
    this.poll = timer(0, MAP_REFRESH_INTERVAL_MS)
      .pipe(
        switchMap(() => this.api.getTrack(icao).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((track) => {
        this.loading.set(false);
        if (track) {
          this.error.set(null);
          this.track.set(track);
          // Let the @if block render the map div before Leaflet touches it.
          queueMicrotask(() => this.drawTrack());
        } else if (!this.track()) {
          this.error.set('Could not load track data for this aircraft.');
        }
        // Refresh failures after a successful load keep the stale data silently.
      });
  }

  private stopWatching(): void {
    this.poll?.unsubscribe();
    this.poll = null;
    this.map?.remove();
    this.map = null;
    this.trackLayer = null;
  }

  private drawTrack(): void {
    const el = this.detailMapEl()?.nativeElement;
    const track = this.track();
    if (!el || !track) {
      return;
    }
    if (!this.map) {
      this.map = L.map(el, { scrollWheelZoom: false }).setView(MAP_CENTER, 7);
      L.tileLayer(TILE_LAYER.urlTemplate, {
        attribution: TILE_LAYER.attribution,
        maxZoom: TILE_LAYER.maxZoom,
      }).addTo(this.map);
      this.trackLayer = L.layerGroup().addTo(this.map);
    }
    const layer = this.trackLayer;
    if (!layer) {
      return;
    }
    layer.clearLayers();
    const pts = track.points.filter(
      (p): p is PlottablePoint => p.lat != null && p.lon != null,
    );
    if (!pts.length) {
      return;
    }
    const latlngs = pts.map((p) => [p.lat, p.lon] as [number, number]);
    L.polyline(latlngs, { color: '#f59e0b', weight: 3 }).addTo(layer);
    const first = latlngs[0];
    const last = latlngs[latlngs.length - 1];
    L.circleMarker(first, { radius: 6, color: '#22c55e', fillColor: '#22c55e', fillOpacity: 1 })
      .bindTooltip('Oldest point')
      .addTo(layer);
    L.circleMarker(last, { radius: 6, color: '#ef4444', fillColor: '#ef4444', fillOpacity: 1 })
      .bindTooltip('Latest point')
      .addTo(layer);
    this.map.fitBounds(L.latLngBounds(latlngs).pad(0.2));
  }
}

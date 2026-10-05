import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, switchMap, timer } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AnomalyDto, StatusDto, TrackDto } from '../../core/models/api.models';
import { LeafletMapComponent } from '../../components/map/leaflet-map/leaflet-map';
import { TrackDetailComponent } from '../../components/map/track-detail/track-detail';
import {
  AircraftMarker,
  MAP_REFRESH_INTERVAL_MS,
  MarkerSeverity,
} from '../../components/map/map.config';

/**
 * Public airspace map. No authentication required, never touches tokens.
 * This is the landing page — the wildcard route falls back here so a
 * redirect loop is impossible by construction.
 *
 * Layout: status bar (fleet counts from /public/status), a live Leaflet map
 * with one marker per anomalous aircraft, and a keyboard-navigable sidebar
 * feed of recent anomalies. Selecting an aircraft (map marker or feed item)
 * opens its track detail. All data re-polls every 30s, matching the backend
 * cadence.
 */
@Component({
  selector: 'app-public-map',
  standalone: true,
  imports: [LeafletMapComponent, TrackDetailComponent, DatePipe, DecimalPipe],
  templateUrl: './public-map.html',
  styleUrl: './public-map.scss',
})
export class PublicMapComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected status: StatusDto | null = null;
  protected anomalies: AnomalyDto[] = [];
  protected markers: AircraftMarker[] = [];
  protected selectedIcao: string | null = null;
  protected loading = true;
  protected error: string | null = null;
  protected lastUpdated: Date | null = null;

  ngOnInit(): void {
    timer(0, MAP_REFRESH_INTERVAL_MS)
      .pipe(
        switchMap(() => this.refresh()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.loading = false;
          this.lastUpdated = new Date();
          this.error =
            !this.status && this.anomalies.length === 0
              ? 'Live data is unavailable right now. Retrying automatically.'
              : null;
        },
        error: () => {
          this.loading = false;
          this.error = 'Live data is unavailable right now. Retrying automatically.';
        },
      });
  }

  /** Select an aircraft: highlights its marker and opens its track detail. */
  select(icaoHex: string): void {
    this.selectedIcao = icaoHex;
  }

  /** Close the track detail panel. */
  clearSelection(): void {
    this.selectedIcao = null;
  }

  /**
   * One refresh cycle: status + recent anomalies, then the track (latest
   * position) for each distinct anomalous aircraft, capped so a busy sky
   * doesn't fan out into hundreds of requests.
   */
  private refresh() {
    return forkJoin({
      status: this.api.getStatus().pipe(catchError(() => of(null))),
      page: this.api.getPublicAnomalies(undefined, 0, 30).pipe(catchError(() => of(null))),
    }).pipe(
      switchMap(({ status, page }: { status: StatusDto | null; page: { content: AnomalyDto[] } | null }) => {
        const anomalies = page?.content ?? [];
        this.status = status;
        this.anomalies = anomalies;
        const icaos = [...new Set(anomalies.map((a) => a.icaoHex))].slice(0, 20);
        if (icaos.length === 0) {
          this.markers = [];
          return of(undefined);
        }
        return forkJoin(
          icaos.map((icao) => this.api.getTrack(icao).pipe(catchError(() => of(null)))),
        ).pipe(
          map((tracks: (TrackDto | null)[]) => {
            this.markers = this.buildMarkers(anomalies, tracks);
          }),
        );
      }),
      map(() => undefined),
    );
  }

  private buildMarkers(anomalies: AnomalyDto[], tracks: (TrackDto | null)[]): AircraftMarker[] {
    const anomalyByIcao = new Map<string, AnomalyDto>();
    for (const a of anomalies) {
      const prev = anomalyByIcao.get(a.icaoHex);
      if (!prev || a.score > prev.score) {
        anomalyByIcao.set(a.icaoHex, a);
      }
    }
    const markers: AircraftMarker[] = [];
    for (const t of tracks) {
      if (!t) {
        continue;
      }
      const latest = [...t.points].reverse().find((p) => p.lat != null && p.lon != null);
      if (latest?.lat == null || latest?.lon == null) {
        continue;
      }
      const anomaly = anomalyByIcao.get(t.aircraft.icaoHex);
      const severity: MarkerSeverity = !anomaly
        ? 'normal'
        : anomaly.escalated
          ? 'escalated'
          : 'anomaly';
      markers.push({
        icaoHex: t.aircraft.icaoHex,
        callsign: t.aircraft.callsign ?? anomaly?.callsign ?? null,
        lat: latest.lat,
        lon: latest.lon,
        severity,
      });
    }
    return markers;
  }
}

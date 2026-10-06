import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';

import {
  AircraftMarker,
  MAP_CENTER,
  MAP_DEFAULT_ZOOM,
  MAP_MAX_ZOOM,
  MAP_MIN_ZOOM,
  MARKER_COLORS,
  TILE_LAYER,
} from '../map.config';

/**
 * Leaflet map for the public SENTINEL view.
 *
 * Renders one marker per aircraft: blue for normal tracked aircraft, amber
 * for anomalous aircraft, red for escalated anomalies. Markers are clickable
 * and keyboard-activatable; activating one emits its ICAO hex so the parent
 * can load the track detail. No auth involved — public data only.
 */
@Component({
  selector: 'app-leaflet-map',
  standalone: true,
  templateUrl: './leaflet-map.html',
  styleUrl: './leaflet-map.scss',
})
export class LeafletMapComponent implements AfterViewInit, OnDestroy {
  /** Aircraft to plot. Markers re-render whenever the input changes. */
  readonly markers = input<AircraftMarker[]>([]);
  /** ICAO hex of the selected aircraft; its marker gets a highlight ring. */
  readonly selectedIcao = input<string | null>(null);
  /** Emitted when a marker is clicked or keyboard-activated. */
  readonly markerClick = output<string>();

  private readonly mapEl = viewChild.required<ElementRef<HTMLDivElement>>('mapEl');

  private map: L.Map | null = null;
  private markerLayer: L.LayerGroup | null = null;

  constructor() {
    // Re-sync markers whenever the inputs change (no-op until the map exists).
    effect(() => {
      this.syncMarkers(this.markers(), this.selectedIcao());
    });
  }

  ngAfterViewInit(): void {
    this.map = L.map(this.mapEl().nativeElement, {
      center: MAP_CENTER,
      zoom: MAP_DEFAULT_ZOOM,
      minZoom: MAP_MIN_ZOOM,
      maxZoom: MAP_MAX_ZOOM,
    });
    L.tileLayer(TILE_LAYER.urlTemplate, {
      attribution: TILE_LAYER.attribution,
      maxZoom: TILE_LAYER.maxZoom,
    }).addTo(this.map);
    this.markerLayer = L.layerGroup().addTo(this.map);
    this.syncMarkers(this.markers(), this.selectedIcao());
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = null;
    this.markerLayer = null;
  }

  /** Leaflet marker click handler. Public so tests can drive it directly. */
  selectMarker(icaoHex: string): void {
    this.markerClick.emit(icaoHex);
  }

  private syncMarkers(markers: AircraftMarker[], selectedIcao: string | null): void {
    const layer = this.markerLayer;
    if (!layer) {
      return;
    }
    layer.clearLayers();
    for (const m of markers) {
      const selected = m.icaoHex === selectedIcao;
      const icon = L.divIcon({
        className: 'sentinel-aircraft-marker',
        html:
          `<span class="dot${selected ? ' selected' : ''}" data-sev="${m.severity}" ` +
          `style="--marker-color: ${MARKER_COLORS[m.severity]}"></span>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10],
      });
      const label = m.callsign?.trim() ? `${m.callsign.trim()} (${m.icaoHex})` : m.icaoHex;
      const marker = L.marker([m.lat, m.lon], {
        icon,
        title: label,
        alt: `Aircraft ${label}`,
        keyboard: true,
      });
      marker.bindTooltip(label, { direction: 'top', offset: [0, -10] });
      marker.on('click', () => this.selectMarker(m.icaoHex));
      marker.addTo(layer);
    }
  }
}

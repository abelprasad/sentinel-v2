/**
 * Shared Leaflet configuration for the SENTINEL public map.
 *
 * Single source of truth for the tile layer, default viewport, marker
 * severity colors, and the live-data refresh cadence. Public components
 * only — nothing here touches auth.
 */

/** [latitude, longitude] tuple, as Leaflet expects. */
export type LatLngTuple = [number, number];

/** Default viewport: Philadelphia metro area. */
export const MAP_CENTER: LatLngTuple = [39.9, -75.1];
export const MAP_DEFAULT_ZOOM = 9;
export const MAP_MIN_ZOOM = 3;
export const MAP_MAX_ZOOM = 18;

export interface TileLayerConfig {
  urlTemplate: string;
  attribution: string;
  maxZoom: number;
}

/** OpenStreetMap standard tile layer. */
export const TILE_LAYER: TileLayerConfig = {
  urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution:
    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  maxZoom: 19,
};

/**
 * Live-data refresh cadence. Matches the backend poll cadence (30s): the
 * public map re-fetches status, anomalies, and tracks on this interval.
 */
export const MAP_REFRESH_INTERVAL_MS = 30_000;

/**
 * Marker severity drives color on the map:
 * - escalated: red    (anomaly flagged as escalated)
 * - anomaly:   amber  (flagged by the anomaly engine)
 * - normal:    blue   (tracked aircraft, no anomaly)
 */
export type MarkerSeverity = 'normal' | 'anomaly' | 'escalated';

export const MARKER_COLORS: Record<MarkerSeverity, string> = {
  normal: '#2563eb',
  anomaly: '#f59e0b',
  escalated: '#ef4444',
};

/** Marker for a single aircraft plotted on the public map. */
export interface AircraftMarker {
  icaoHex: string;
  callsign: string | null;
  lat: number;
  lon: number;
  severity: MarkerSeverity;
}

/** Mirrors backend StatusDto. */
export interface StatusDto {
  status: string;
  service: string;
  time: string;
  aircraftTracked: number;
  activeTracks: number;
  eventsLastHour: number;
  unacknowledgedAnomalies: number;
  anomaliesLast24h: number;
}

/** Mirrors backend AircraftDto. */
export interface AircraftDto {
  icaoHex: string;
  callsign: string | null;
  category: string | null;
  trackState: string;
  firstSeen: string;
  lastSeen: string;
}

/** Mirrors backend AnomalyDto. */
export interface AnomalyDto {
  id: number;
  icaoHex: string;
  callsign: string | null;
  score: number;
  zAltitude: number | null;
  zSpeed: number | null;
  zHeading: number | null;
  zPosition: number | null;
  explanation: string | null;
  explanationSrc: string | null;
  parentAnomalyId: number | null;
  acknowledged: boolean;
  escalated: boolean;
  flaggedAt: string;
}

/** Mirrors backend TrackPointDto. */
export interface TrackPointDto {
  lat: number | null;
  lon: number | null;
  altitudeFt: number | null;
  speedKts: number | null;
  headingDeg: number | null;
  recordedAt: string;
}

/** Mirrors backend TrackDto. */
export interface TrackDto {
  aircraft: AircraftDto;
  points: TrackPointDto[];
}

/** Mirrors backend BaselineDto. */
export interface BaselineDto {
  aircraftId: number;
  icaoHex: string;
  sampleCount: number;
  meanAltitudeFt: number | null;
  meanSpeedKts: number | null;
  meanHeadingDeg: number | null;
  lastUpdated: string;
}

/** Generic paged response wrapper. */
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

/** Uniform error body from the backend. */
export interface ErrorDto {
  status: number;
  message: string;
  path: string;
  timestamp: string;
}

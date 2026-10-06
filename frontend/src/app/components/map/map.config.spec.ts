import {
  MAP_CENTER,
  MAP_DEFAULT_ZOOM,
  MAP_REFRESH_INTERVAL_MS,
  MARKER_COLORS,
  TILE_LAYER,
} from './map.config';

describe('map.config', () => {
  it('centers the default viewport on the Philadelphia area', () => {
    expect(MAP_CENTER).toEqual([39.9, -75.1]);
  });

  it('uses zoom 9 by default', () => {
    expect(MAP_DEFAULT_ZOOM).toBe(9);
  });

  it('uses CartoDB dark tiles with attribution', () => {
    expect(TILE_LAYER.urlTemplate).toContain('basemaps.cartocdn.com/dark_all');
    expect(TILE_LAYER.attribution).toContain('OpenStreetMap');
    expect(TILE_LAYER.attribution).toContain('CARTO');
  });

  it('refreshes live data every 30s, matching the backend poll cadence', () => {
    expect(MAP_REFRESH_INTERVAL_MS).toBe(30_000);
  });

  it('colors escalated markers red, anomalies amber, normal aircraft cyan', () => {
    expect(MARKER_COLORS.escalated).toBe('#f87171');
    expect(MARKER_COLORS.anomaly).toBe('#fbbf24');
    expect(MARKER_COLORS.normal).toBe('#22d3ee');
  });
});

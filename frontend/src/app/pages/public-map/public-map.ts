import { Component } from '@angular/core';

/**
 * Public airspace map. No authentication required, never touches tokens.
 * This is the landing page — the wildcard route falls back here so a
 * redirect loop is impossible by construction.
 */
@Component({
  selector: 'app-public-map',
  standalone: true,
  template: `
    <main>
      <h1>SENTINEL</h1>
      <p>Live airspace anomaly detection — public view.</p>
      <!-- Leaflet map renders here (Phase 8) -->
      <div id="map" aria-label="Live aircraft map"></div>
    </main>
  `,
})
export class PublicMapComponent {}

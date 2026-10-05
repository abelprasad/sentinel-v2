import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LeafletMapComponent } from './leaflet-map';
import { AircraftMarker } from '../map.config';

const MARKERS: AircraftMarker[] = [
  { icaoHex: 'a1b2c3', callsign: 'UAL123', lat: 39.9, lon: -75.1, severity: 'anomaly' },
  { icaoHex: 'd4e5f6', callsign: null, lat: 40.0, lon: -75.2, severity: 'escalated' },
];

describe('LeafletMapComponent', () => {
  let fixture: ComponentFixture<LeafletMapComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeafletMapComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(LeafletMapComponent);
    fixture.componentRef.setInput('markers', MARKERS);
    fixture.detectChanges();
  });

  it('creates the map with an accessible label', () => {
    const mapDiv = fixture.nativeElement.querySelector('.leaflet-map') as HTMLElement | null;
    expect(mapDiv?.getAttribute('role')).toBe('application');
    expect(mapDiv?.getAttribute('aria-label')).toContain('Philadelphia');
  });

  it('renders one marker per aircraft', () => {
    const dots = fixture.nativeElement.querySelectorAll('.sentinel-aircraft-marker .dot');
    expect(dots.length).toBe(MARKERS.length);
  });

  it('colors markers by severity', () => {
    const dots = [
      ...(fixture.nativeElement.querySelectorAll(
        '.sentinel-aircraft-marker .dot',
      ) as NodeListOf<HTMLElement>),
    ];
    expect(dots[0].style.getPropertyValue('--marker-color')).toBe('#f59e0b');
    expect(dots[1].style.getPropertyValue('--marker-color')).toBe('#ef4444');
  });

  it('highlights the selected marker', () => {
    fixture.componentRef.setInput('selectedIcao', 'a1b2c3');
    fixture.detectChanges();
    const selected = fixture.nativeElement.querySelectorAll(
      '.sentinel-aircraft-marker .dot.selected',
    );
    expect(selected.length).toBe(1);
  });

  it('emits markerClick when a marker is selected', () => {
    const emitted: string[] = [];
    fixture.componentInstance.markerClick.subscribe((icao) => emitted.push(icao));
    fixture.componentInstance.selectMarker('d4e5f6');
    expect(emitted).toEqual(['d4e5f6']);
  });
});

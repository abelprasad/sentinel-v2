import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { TrackDetailComponent } from './track-detail';
import { ApiService } from '../../../core/api.service';
import { TrackDto } from '../../../core/models/api.models';

const TRACK: TrackDto = {
  aircraft: {
    icaoHex: 'a1b2c3',
    callsign: 'UAL123',
    category: 'A3',
    trackState: 'ACTIVE',
    firstSeen: '2026-10-05T16:00:00Z',
    lastSeen: '2026-10-05T18:00:00Z',
  },
  points: Array.from({ length: 12 }, (_, i) => ({
    lat: 39.9 + i * 0.01,
    lon: -75.1 + i * 0.01,
    altitudeFt: 5000 + i * 100,
    speedKts: 250,
    headingDeg: 90,
    recordedAt: `2026-10-05T17:${String(i).padStart(2, '0')}:00Z`,
  })),
};

describe('TrackDetailComponent', () => {
  let fixture: ComponentFixture<TrackDetailComponent>;
  let getTrackImpl: (icao: string) => Observable<TrackDto | null>;

  beforeEach(async () => {
    getTrackImpl = () => of(TRACK);
    await TestBed.configureTestingModule({
      imports: [TrackDetailComponent],
      providers: [{ provide: ApiService, useValue: { getTrack: (icao: string) => getTrackImpl(icao) } }],
    }).compileComponents();
    fixture = TestBed.createComponent(TrackDetailComponent);
  });

  async function open(icao: string | null = 'a1b2c3'): Promise<void> {
    fixture.componentRef.setInput('icaoHex', icao);
    fixture.detectChanges();
    await flushAsync();
    fixture.detectChanges();
  }

  /** Real-timer flush: the app is zoneless, so whenStable() cannot see setTimeout. */
  function flushAsync(ms = 50): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  it('renders nothing when no aircraft is selected', async () => {
    fixture.detectChanges();
    await flushAsync();
    expect(fixture.nativeElement.querySelector('.track-detail')).toBeNull();
  });

  it('renders the callsign and ICAO for the selected aircraft', async () => {
    await open();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.track-detail h2')?.textContent).toContain('UAL123');
    expect(el.querySelector('.track-detail .icao')?.textContent).toContain('a1b2c3');
  });

  it('renders the latest 10 telemetry points, newest first', async () => {
    await open();
    const rows = fixture.nativeElement.querySelectorAll('.telemetry tbody tr');
    expect(rows.length).toBe(10);
    // Newest point (i=11, alt 6100) comes first.
    expect(rows[0].textContent).toContain('6100');
    expect(rows[9].textContent).toContain('5200');
  });

  it('shows an error when the track fails to load', async () => {
    getTrackImpl = () => throwError(() => new Error('boom'));
    await open();
    const alert = fixture.nativeElement.querySelector('.track-detail [role="alert"]');
    expect(alert?.textContent).toContain('Could not load track data');
  });

  it('emits closed when the close button is clicked', async () => {
    const emitted: boolean[] = [];
    fixture.componentInstance.closed.subscribe(() => emitted.push(true));
    await open();
    (fixture.nativeElement.querySelector('.close-btn') as HTMLButtonElement).click();
    expect(emitted).toEqual([true]);
  });
});

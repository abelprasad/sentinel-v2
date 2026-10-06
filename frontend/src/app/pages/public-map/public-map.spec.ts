import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { PublicMapComponent } from './public-map';
import { ApiService } from '../../core/api.service';
import { AnomalyDto, PagedResponse, StatusDto, TrackDto } from '../../core/models/api.models';

const STATUS: StatusDto = {
  status: 'ok',
  service: 'sentinel',
  time: '2026-10-05T18:00:00Z',
  aircraftTracked: 42,
  activeTracks: 40,
  eventsLastHour: 120,
  unacknowledgedAnomalies: 3,
  anomaliesLast24h: 7,
};

function anomaly(id: number, icaoHex: string, callsign: string | null, escalated: boolean): AnomalyDto {
  return {
    id,
    icaoHex,
    callsign,
    score: 4.2,
    zAltitude: 3.1,
    zSpeed: null,
    zHeading: null,
    zPosition: null,
    explanation: 'Altitude 3.1σ above baseline',
    explanationSrc: 'rule',
    parentAnomalyId: null,
    acknowledged: false,
    escalated,
    flaggedAt: '2026-10-05T17:30:00Z',
  };
}

const ANOMALY_PAGE: PagedResponse<AnomalyDto> = {
  content: [
    anomaly(1, 'a1b2c3', 'UAL123', true),
    anomaly(2, 'd4e5f6', null, false),
  ],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 30,
};

const TRACK: TrackDto = {
  aircraft: {
    icaoHex: 'a1b2c3',
    callsign: 'UAL123',
    category: 'A3',
    trackState: 'ACTIVE',
    firstSeen: '2026-10-05T16:00:00Z',
    lastSeen: '2026-10-05T18:00:00Z',
  },
  points: [
    {
      lat: 39.9,
      lon: -75.1,
      altitudeFt: 5000,
      speedKts: 250,
      headingDeg: 90,
      recordedAt: '2026-10-05T17:59:00Z',
    },
  ],
};

describe('PublicMapComponent', () => {
  let fixture: ComponentFixture<PublicMapComponent>;
  let failApi: boolean;

  beforeEach(async () => {
    failApi = false;
    const apiStub = {
      getStatus: (): Observable<StatusDto> => (failApi ? throwError(() => new Error('down')) : of(STATUS)),
      getPublicAnomalies: (): Observable<PagedResponse<AnomalyDto>> =>
        failApi ? throwError(() => new Error('down')) : of(ANOMALY_PAGE),
      getTrack: (): Observable<TrackDto> => (failApi ? throwError(() => new Error('down')) : of(TRACK)),
    };
    await TestBed.configureTestingModule({
      imports: [PublicMapComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: apiStub }],
    }).compileComponents();
    fixture = TestBed.createComponent(PublicMapComponent);
  });

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await flushAsync();
    fixture.detectChanges();
  }

  /** Real-timer flush: the app is zoneless, so whenStable() cannot see setTimeout. */
  function flushAsync(ms = 50): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  it('shows fleet counts in the status bar', async () => {
    await settle();
    const values = [
      ...(fixture.nativeElement.querySelectorAll(
        '.statusbar .stat .value',
      ) as NodeListOf<HTMLElement>),
    ].map((el) => el.textContent?.trim());
    expect(values).toEqual(['42', '40', '7', '120']);
  });

  it('lists recent anomalies in the keyboard-navigable sidebar feed', async () => {
    await settle();
    const buttons = fixture.nativeElement.querySelectorAll('.anomaly-feed button');
    expect(buttons.length).toBe(2);
    expect(buttons[0].textContent).toContain('UAL123');
    expect(buttons[1].textContent).toContain('d4e5f6');
    expect(buttons[0].querySelector('.badge-crit')?.textContent).toContain('Escalated');
  });

  it('opens the track detail when a feed item is selected', async () => {
    await settle();
    (fixture.nativeElement.querySelector('.anomaly-feed button') as HTMLButtonElement).click();
    await settle();
    const detail = fixture.nativeElement.querySelector('app-track-detail .track-detail');
    expect(detail).toBeTruthy();
    expect(detail.textContent).toContain('UAL123');
  });

  it('shows an error banner when the API is unreachable', async () => {
    failApi = true;
    await settle();
    const banner = fixture.nativeElement.querySelector('.banner[role="alert"]');
    expect(banner?.textContent).toContain('unavailable');
  });
});

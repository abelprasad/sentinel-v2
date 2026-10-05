import { TestBed } from '@angular/core/testing';
import { of, Observable, throwError } from 'rxjs';

import { AnomalyListComponent } from './anomaly-list';
import { ApiService } from '../../core/api.service';
import { AnomalyDto, PagedResponse } from '../../core/models/api.models';

const A1: AnomalyDto = {
  id: 10,
  icaoHex: 'A1B2C3',
  callsign: 'N123AB',
  score: 8.5,
  zAltitude: 0.4,
  zSpeed: 0.9,
  zHeading: 0.2,
  zPosition: 9.4,
  explanation: 'Spoofing signature detected.',
  explanationSrc: 'rule',
  parentAnomalyId: 1042,
  acknowledged: false,
  escalated: true,
  flaggedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
};

const A2: AnomalyDto = {
  id: 11,
  icaoHex: 'D4E5F6',
  callsign: null,
  score: 1.5,
  zAltitude: -1.1,
  zSpeed: 0.3,
  zHeading: 0.1,
  zPosition: 0.2,
  explanation: null,
  explanationSrc: null,
  parentAnomalyId: null,
  acknowledged: true,
  escalated: false,
  flaggedAt: new Date(Date.now() - 26 * 60 * 60 * 1000).toISOString(),
};

function paged(content: AnomalyDto[], pageNum = 0, totalPages = 3): PagedResponse<AnomalyDto> {
  return { content, totalElements: 50, totalPages, page: pageNum, size: 20 };
}

describe('AnomalyListComponent', () => {
  let calls: { icaoHex?: string; page: number; size: number }[];
  let getPublicAnomalies: (icaoHex?: string, page?: number, size?: number) => Observable<PagedResponse<AnomalyDto>>;

  async function create() {
    calls = [];
    getPublicAnomalies = (icaoHex?: string, page = 0, size = 20) => {
      calls.push({ icaoHex, page, size });
      return of(paged(icaoHex ? [A1] : [A1, A2]));
    };
    await TestBed.configureTestingModule({
      imports: [AnomalyListComponent],
      providers: [{ provide: ApiService, useValue: { getPublicAnomalies } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('should create and load the first page', async () => {
    const fixture = await create();
    expect(fixture.componentInstance).toBeTruthy();
    expect(calls.length).toBe(1);
    expect(calls[0].page).toBe(0);
    expect(calls[0].icaoHex).toBeUndefined();
  });

  it('should render a row per anomaly with score, callsign, and time', async () => {
    const fixture = await create();
    const rows = fixture.nativeElement.querySelectorAll('.anomaly-card');
    expect(rows.length).toBe(2);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('N123AB');
    expect(text).toContain('8.5');
    expect(text).toContain('5m ago');
    expect(text).toContain('UNKNOWN'); // null callsign fallback
    expect(text).toContain('1d ago');
  });

  it('should color the score badge by severity', async () => {
    const fixture = await create();
    const badges = fixture.nativeElement.querySelectorAll('.score-badge');
    expect(badges[0].className).toContain('sev-crit');
    expect(badges[1].className).toContain('sev-low');
  });

  it('should show ACK and ESCALATED indicators', async () => {
    const fixture = await create();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('ACK');
    expect(text).toContain('ESCALATED');
  });

  it('should paginate forward and back', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    comp.nextPage();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(calls[calls.length - 1].page).toBe(1);

    comp.prevPage();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(calls[calls.length - 1].page).toBe(0);
  });

  it('should filter by ICAO hex when the filter form is submitted', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    comp.icaoFilter = 'a1b2c3';
    comp.applyFilter();
    fixture.detectChanges();
    await fixture.whenStable();

    const last = calls[calls.length - 1];
    expect(last.icaoHex).toBe('A1B2C3');
    expect(last.page).toBe(0);
  });

  it('should expand a detail panel on toggle with aria-expanded', async () => {
    const fixture = await create();
    const button = fixture.nativeElement.querySelector('.row-toggle') as HTMLButtonElement;
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('app-anomaly-detail')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Signal breakdown');
  });

  it('should show an error message when the API fails', async () => {
    await TestBed.configureTestingModule({
      imports: [AnomalyListComponent],
      providers: [
        {
          provide: ApiService,
          useValue: {
            getPublicAnomalies: () => throwError(() => new Error('offline')),
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Could not load anomalies');
  });
});

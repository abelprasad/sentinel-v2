import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AnomalyListComponent } from './anomaly-list';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { AnomalyDto } from '../../../core/models/api.models';

function anomaly(overrides: Partial<AnomalyDto> = {}): AnomalyDto {
  return {
    id: 7,
    icaoHex: 'A1B2C3',
    callsign: 'SWA123',
    score: 4.2,
    zAltitude: 2.31,
    zSpeed: -0.42,
    zHeading: 0.1,
    zPosition: 3.05,
    explanation: 'Climbed 2000 ft in 30 s.',
    explanationSrc: null,
    parentAnomalyId: null,
    acknowledged: false,
    escalated: false,
    flaggedAt: '2026-10-05T11:30:00Z',
    ...overrides,
  };
}

const PAGE = (rows: AnomalyDto[]) => ({ content: rows, totalElements: rows.length, totalPages: 1, page: 0, size: 20 });

describe('AnomalyListComponent', () => {
  const getAnomalies = vi.fn();
  const acknowledgeAnomaly = vi.fn();
  const escalateAnomaly = vi.fn();
  const deEscalateAnomaly = vi.fn();
  const logout = vi.fn();
  const navigate = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    getAnomalies.mockReturnValue(of(PAGE([anomaly()])));
    await TestBed.configureTestingModule({
      imports: [AnomalyListComponent],
      providers: [
        { provide: ApiService, useValue: { getAnomalies, acknowledgeAnomaly, escalateAnomaly, deEscalateAnomaly } },
        { provide: AuthService, useValue: { logout } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
  });

  it('renders rows with z-score detail', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('A1B2C3');
    expect(el.textContent).toContain('+2.31\u03c3'); // zAltitude
    expect(el.textContent).toContain('-0.42\u03c3'); // zSpeed
    expect(el.textContent).toContain('Climbed 2000 ft in 30 s.');
  });

  it('acknowledges an anomaly and updates the row in place', () => {
    acknowledgeAnomaly.mockReturnValue(of(anomaly({ acknowledged: true })));
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const ackBtn = Array.from(el.querySelectorAll('.card-actions .ghost-btn')).find((b) =>
      b.textContent?.includes('Acknowledge'),
    ) as HTMLElement;
    ackBtn.click();
    fixture.detectChanges();

    expect(acknowledgeAnomaly).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.rows()[0].acknowledged).toBe(true);
    expect(el.textContent).toContain('Reviewed');
  });

  it('toggles escalation and de-escalation', () => {
    escalateAnomaly.mockReturnValue(of(anomaly({ escalated: true })));
    deEscalateAnomaly.mockReturnValue(of(anomaly({ escalated: false })));
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const escBtn = () =>
      Array.from(el.querySelectorAll('.card-actions button')).find((b) =>
        /escalate/i.test(b.textContent ?? ''),
      ) as HTMLElement;

    escBtn().click(); // escalate
    fixture.detectChanges();
    expect(escalateAnomaly).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.rows()[0].escalated).toBe(true);

    escBtn().click(); // de-escalate
    fixture.detectChanges();
    expect(deEscalateAnomaly).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.rows()[0].escalated).toBe(false);
  });

  it('filters to unacknowledged anomalies only', () => {
    getAnomalies.mockReturnValue(of(PAGE([anomaly({ id: 1 }), anomaly({ id: 2, acknowledged: true })])));
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.visibleRows().length).toBe(2);

    const el = fixture.nativeElement as HTMLElement;
    const checkbox = el.querySelector('.filter-toggle input') as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.componentInstance.visibleRows().length).toBe(1);
    expect(fixture.componentInstance.visibleRows()[0].id).toBe(1);
  });

  it('redirects to login on 401', () => {
    getAnomalies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/admin/login']);
  });
});

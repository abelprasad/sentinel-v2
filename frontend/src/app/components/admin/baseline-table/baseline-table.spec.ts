import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BaselineTableComponent } from './baseline-table';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { BaselineDto } from '../../../core/models/api.models';

const ROW: BaselineDto = {
  aircraftId: 9,
  icaoHex: 'A1B2C3',
  sampleCount: 120,
  meanAltitudeFt: 31000,
  meanSpeedKts: 445.5,
  meanHeadingDeg: 270.2,
  lastUpdated: '2026-10-05T11:00:00Z',
};

const PAGE = { content: [ROW], totalElements: 1, totalPages: 1, page: 0, size: 20 };

describe('BaselineTableComponent', () => {
  const getBaselines = vi.fn().mockReturnValue(of(PAGE));
  const resetBaseline = vi.fn().mockReturnValue(of(undefined));
  const logout = vi.fn();
  const navigate = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    getBaselines.mockReturnValue(of(PAGE));
    resetBaseline.mockReturnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [BaselineTableComponent],
      providers: [
        { provide: ApiService, useValue: { getBaselines, resetBaseline } },
        { provide: AuthService, useValue: { logout } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
  });

  it('renders baseline stats per aircraft', () => {
    const fixture = TestBed.createComponent(BaselineTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('A1B2C3');
    expect(el.textContent).toContain('120'); // sample count
    expect(el.textContent).toContain('31,000'); // mean altitude
  });

  it('asks for confirmation before resetting', () => {
    const fixture = TestBed.createComponent(BaselineTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();

    (el.querySelector('.warn-btn') as HTMLElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[role="alertdialog"]')).toBeTruthy();
    expect(resetBaseline).not.toHaveBeenCalled();
  });

  it('resets after confirmation and shows a notice', () => {
    const fixture = TestBed.createComponent(BaselineTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('.warn-btn') as HTMLElement).click();
    fixture.detectChanges();
    const dialog = el.querySelector('[role="alertdialog"]') as HTMLElement;
    (dialog.querySelector('.warn-btn.solid') as HTMLElement).click();
    fixture.detectChanges();

    expect(resetBaseline).toHaveBeenCalledWith(9);
    expect(el.textContent).toContain('Baseline reset for A1B2C3');
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('redirects to login on 401', () => {
    getBaselines.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = TestBed.createComponent(BaselineTableComponent);
    fixture.detectChanges();
    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/admin/login']);
  });
});

import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AdminDashboardComponent } from './admin-dashboard';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { StatusDto } from '../../core/models/api.models';

const STATUS: StatusDto = {
  status: 'UP',
  service: 'sentinel',
  time: '2026-10-05T12:00:00Z',
  aircraftTracked: 42,
  activeTracks: 17,
  eventsLastHour: 9001,
  unacknowledgedAnomalies: 3,
  anomaliesLast24h: 11,
};

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 1, page: 0, size: 20 };

describe('AdminDashboardComponent', () => {
  const getStatus = vi.fn().mockReturnValue(of(STATUS));
  const logout = vi.fn();
  const navigate = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    getStatus.mockReturnValue(of(STATUS));
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        {
          provide: ApiService,
          useValue: {
            getStatus,
            getAircraft: vi.fn().mockReturnValue(of(EMPTY_PAGE)),
            getAnomalies: vi.fn().mockReturnValue(of(EMPTY_PAGE)),
            getBaselines: vi.fn().mockReturnValue(of(EMPTY_PAGE)),
          },
        },
        { provide: AuthService, useValue: { logout } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
  });

  it('loads and renders the system status overview', () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    expect(getStatus).toHaveBeenCalled();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('42'); // aircraft tracked
    expect(el.textContent).toContain('17'); // active tracks
  });

  it('switches tabs and renders the aircraft panel', () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const tabs = Array.from(el.querySelectorAll('[role="tab"]')) as HTMLElement[];
    tabs.find((t) => t.textContent?.trim() === 'Aircraft')?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.activeTab()).toBe('aircraft');
    expect(el.querySelector('app-aircraft-table')).toBeTruthy();
  });

  it('logs out via AuthService when the sign-out button is clicked', () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('.logout-btn') as HTMLElement).click();
    expect(logout).toHaveBeenCalled();
  });

  it('redirects to login on 401 when loading status', () => {
    getStatus.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/admin/login']);
    expect(fixture.componentInstance.statusError()).toBeNull();
  });

  it('shows a non-401 status error inline', () => {
    getStatus.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    expect(logout).not.toHaveBeenCalled();
    expect(fixture.componentInstance.statusError()).toBe('Could not load system status.');
  });
});

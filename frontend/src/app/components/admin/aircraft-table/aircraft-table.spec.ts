import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AircraftTableComponent } from './aircraft-table';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { AircraftDto } from '../../../core/models/api.models';

const ROW = {
  id: 42,
  icaoHex: 'A1B2C3',
  callsign: 'SWA123',
  category: 'A3',
  trackState: 'ACTIVE',
  firstSeen: '2026-10-05T10:00:00Z',
  lastSeen: '2026-10-05T11:00:00Z',
} as unknown as AircraftDto;

const PAGE = { content: [ROW], totalElements: 1, totalPages: 1, page: 0, size: 20 };

describe('AircraftTableComponent', () => {
  const getAircraft = vi.fn().mockReturnValue(of(PAGE));
  const deleteAircraft = vi.fn().mockReturnValue(of(undefined));
  const logout = vi.fn();
  const navigate = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    getAircraft.mockReturnValue(of(PAGE));
    deleteAircraft.mockReturnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [AircraftTableComponent],
      providers: [
        { provide: ApiService, useValue: { getAircraft, deleteAircraft } },
        { provide: AuthService, useValue: { logout } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
  });

  it('renders the aircraft rows', () => {
    const fixture = TestBed.createComponent(AircraftTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('A1B2C3');
    expect(el.textContent).toContain('SWA123');
  });

  it('asks for confirmation before deleting', () => {
    const fixture = TestBed.createComponent(AircraftTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();

    (el.querySelector('.danger-btn') as HTMLElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[role="alertdialog"]')).toBeTruthy();
    expect(deleteAircraft).not.toHaveBeenCalled();
  });

  it('deletes after confirmation and reloads', () => {
    const fixture = TestBed.createComponent(AircraftTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('.danger-btn') as HTMLElement).click();
    fixture.detectChanges();
    const dialog = el.querySelector('[role="alertdialog"]') as HTMLElement;
    (dialog.querySelector('.danger-btn.solid') as HTMLElement).click();
    fixture.detectChanges();

    expect(deleteAircraft).toHaveBeenCalledWith(42);
    expect(getAircraft).toHaveBeenCalledTimes(2); // initial load + reload
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('cancels the delete without calling the API', () => {
    const fixture = TestBed.createComponent(AircraftTableComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('.danger-btn') as HTMLElement).click();
    fixture.detectChanges();
    const dialog = el.querySelector('[role="alertdialog"]') as HTMLElement;
    (dialog.querySelector('.ghost-btn') as HTMLElement).click();
    fixture.detectChanges();

    expect(deleteAircraft).not.toHaveBeenCalled();
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('redirects to login on 401', () => {
    getAircraft.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = TestBed.createComponent(AircraftTableComponent);
    fixture.detectChanges();
    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/admin/login']);
  });
});

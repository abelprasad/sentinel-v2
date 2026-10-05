import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AdminLoginComponent } from './admin-login';
import { AuthService } from '../../core/auth.service';

describe('AdminLoginComponent', () => {
  const isAuthenticated = vi.fn().mockReturnValue(false);
  const login = vi.fn();
  const logout = vi.fn();
  const navigate = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    isAuthenticated.mockReturnValue(false);
    await TestBed.configureTestingModule({
      imports: [AdminLoginComponent],
      providers: [
        { provide: AuthService, useValue: { isAuthenticated, login, logout } },
        { provide: Router, useValue: { navigate } },
        { provide: ActivatedRoute, useValue: { snapshot: {} } },
      ],
    }).compileComponents();
  });

  it('redirects to /admin when already authenticated', () => {
    isAuthenticated.mockReturnValue(true);
    TestBed.createComponent(AdminLoginComponent);
    expect(navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('does not redirect when unauthenticated', () => {
    TestBed.createComponent(AdminLoginComponent);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not call login when the form is invalid', () => {
    const fixture = TestBed.createComponent(AdminLoginComponent);
    fixture.componentInstance.submit();
    expect(login).not.toHaveBeenCalled();
  });

  it('navigates to /admin on successful login', () => {
    login.mockReturnValue(of({ token: 't', username: 'admin', role: 'ADMIN' }));
    const fixture = TestBed.createComponent(AdminLoginComponent);
    fixture.componentInstance.form.setValue({ username: 'admin', password: 's3cret' });
    fixture.componentInstance.submit();
    expect(login).toHaveBeenCalledWith('admin', 's3cret');
    expect(navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('shows the backend error message on failed login', () => {
    login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Bad credentials' } })),
    );
    const fixture = TestBed.createComponent(AdminLoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ username: 'admin', password: 'wrong' });
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('Bad credentials');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.form-error')?.textContent).toContain('Bad credentials');
  });

  it('shows a fallback message when the backend sends none', () => {
    login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: null })));
    const fixture = TestBed.createComponent(AdminLoginComponent);
    fixture.componentInstance.form.setValue({ username: 'admin', password: 'x' });
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.error()).toBe('Sign-in failed. Please try again.');
  });
});

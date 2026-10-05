import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  StatusDto,
  TrackDto,
  AnomalyDto,
  BaselineDto,
  AircraftDto,
  PagedResponse,
  LoginResponse,
} from './models/api.models';

/**
 * Typed client for the SENTINEL backend.
 *
 * The base is a RELATIVE path — nginx proxies /api to the backend.
 * No IPs or hostnames are hardcoded anywhere in the frontend.
 * This was a v1 bug: the API base was baked in as http://100.98.50.85:8888,
 * unreachable from the public internet.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  // ---- Public (no auth) ----

  getStatus(): Observable<StatusDto> {
    return this.http.get<StatusDto>(`${this.base}/public/status`);
  }

  getTrack(icaoHex: string): Observable<TrackDto> {
    return this.http.get<TrackDto>(`${this.base}/public/tracks/${icaoHex}`);
  }

  getPublicAnomalies(icaoHex?: string, page = 0, size = 20): Observable<PagedResponse<AnomalyDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (icaoHex) params = params.set('icaoHex', icaoHex);
    return this.http.get<PagedResponse<AnomalyDto>>(`${this.base}/public/anomalies`, { params });
  }

  // ---- Admin (JWT required, attached by the auth interceptor) ----

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/auth/login`, { username, password });
  }

  getAircraft(page = 0, size = 20): Observable<PagedResponse<AircraftDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<AircraftDto>>(`${this.base}/admin/aircraft`, { params });
  }

  deleteAircraft(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/aircraft/${id}`);
  }

  getAnomalies(page = 0, size = 20): Observable<PagedResponse<AnomalyDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<AnomalyDto>>(`${this.base}/admin/anomalies`, { params });
  }

  acknowledgeAnomaly(id: number): Observable<AnomalyDto> {
    return this.http.post<AnomalyDto>(`${this.base}/admin/anomalies/${id}/acknowledge`, {});
  }

  escalateAnomaly(id: number): Observable<AnomalyDto> {
    return this.http.post<AnomalyDto>(`${this.base}/admin/anomalies/${id}/escalate`, {});
  }

  deEscalateAnomaly(id: number): Observable<AnomalyDto> {
    return this.http.delete<AnomalyDto>(`${this.base}/admin/anomalies/${id}/escalate`);
  }

  getBaselines(page = 0, size = 20): Observable<PagedResponse<BaselineDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<BaselineDto>>(`${this.base}/admin/baselines`, { params });
  }

  resetBaseline(aircraftId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/baselines/${aircraftId}/reset`, {});
  }

  health(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>(`${this.base}/health`);
  }
}

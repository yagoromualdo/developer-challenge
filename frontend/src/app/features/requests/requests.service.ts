import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/http/api';
import { Page } from '../../core/http/page';
import { RequestListQuery, toHttpParams } from './request-list-query';
import { CostCenter, PurchaseRequestSummary } from './requests.models';

@Injectable({ providedIn: 'root' })
export class RequestsService {
  private readonly http = inject(HttpClient);

  /** A visibilidade por papel é aplicada pelo back; o front só repassa os filtros. */
  list(query: RequestListQuery): Observable<Page<PurchaseRequestSummary>> {
    return this.http.get<Page<PurchaseRequestSummary>>(`${API_BASE_URL}/requests`, {
      params: toHttpParams(query),
    });
  }

  costCenters(): Observable<CostCenter[]> {
    return this.http.get<CostCenter[]>(`${API_BASE_URL}/cost-centers`);
  }
}

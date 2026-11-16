import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/http/api';
import { Page } from '../../core/http/page';
import { RequestListQuery, toHttpParams } from './request-list-query';
import {
  CostCenter,
  CreateRequestPayload,
  PurchaseRequestDetail,
  PurchaseRequestSummary,
  RequestEvent,
  RequestVersion,
  RequestVersionSummary,
  TransitionAction,
  UpdateRequestPayload,
} from './requests.models';

export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';

const TRANSITION_PATHS: Record<TransitionAction, string> = {
  SUBMIT: 'submit',
  APPROVE: 'approve',
  REJECT: 'reject',
  REQUEST_CHANGES: 'request-changes',
  CANCEL: 'cancel',
};

@Injectable({ providedIn: 'root' })
export class RequestsService {
  private readonly http = inject(HttpClient);

  /** A visibilidade por papel é aplicada pelo back; o front só repassa os filtros. */
  list(query: RequestListQuery): Observable<Page<PurchaseRequestSummary>> {
    return this.http.get<Page<PurchaseRequestSummary>>(`${API_BASE_URL}/requests`, {
      params: toHttpParams(query),
    });
  }

  get(id: number): Observable<PurchaseRequestDetail> {
    return this.http.get<PurchaseRequestDetail>(`${API_BASE_URL}/requests/${id}`);
  }

  /** Repetir a mesma `idempotencyKey` com o mesmo corpo devolve a solicitação já criada, sem duplicar. */
  create(payload: CreateRequestPayload, idempotencyKey: string): Observable<PurchaseRequestDetail> {
    return this.http.post<PurchaseRequestDetail>(`${API_BASE_URL}/requests`, payload, {
      headers: { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey },
    });
  }

  update(id: number, payload: UpdateRequestPayload): Observable<PurchaseRequestDetail> {
    return this.http.put<PurchaseRequestDetail>(`${API_BASE_URL}/requests/${id}`, payload);
  }

  /**
   * Executa a transição sobre o `lockVersion` lido no detalhe (se ele mudou no meio tempo, o back responde 409).
   * O comentário só vai no corpo quando preenchido: `SUBMIT` não tem comentário.
   */
  transition(
    id: number,
    action: TransitionAction,
    lockVersion: number,
    comment?: string,
  ): Observable<PurchaseRequestDetail> {
    const body = comment ? { lockVersion, comment } : { lockVersion };
    return this.http.post<PurchaseRequestDetail>(
      `${API_BASE_URL}/requests/${id}/${TRANSITION_PATHS[action]}`,
      body,
    );
  }

  versions(id: number): Observable<RequestVersionSummary[]> {
    return this.http.get<RequestVersionSummary[]>(`${API_BASE_URL}/requests/${id}/versions`);
  }

  version(id: number, version: number): Observable<RequestVersion> {
    return this.http.get<RequestVersion>(`${API_BASE_URL}/requests/${id}/versions/${version}`);
  }

  history(id: number): Observable<RequestEvent[]> {
    return this.http.get<RequestEvent[]>(`${API_BASE_URL}/requests/${id}/history`);
  }

  costCenters(): Observable<CostCenter[]> {
    return this.http.get<CostCenter[]>(`${API_BASE_URL}/cost-centers`);
  }
}

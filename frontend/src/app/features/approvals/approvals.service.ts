import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/http/api';
import { ApprovalInboxItem } from './approvals.models';

@Injectable({ providedIn: 'root' })
export class ApprovalsService {
  private readonly http = inject(HttpClient);

  /** Lista sem paginação, da submissão mais antiga para a mais nova (ordem do back). */
  inbox(): Observable<ApprovalInboxItem[]> {
    return this.http.get<ApprovalInboxItem[]>(`${API_BASE_URL}/approvals/inbox`);
  }
}

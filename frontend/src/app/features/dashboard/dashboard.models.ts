import { RequestStatus } from '../../shared/request-status';

/** Resposta de `GET /dashboard/summary`; o escopo (papel do usuário) é decidido pelo back. */
export interface DashboardSummary {
  /** Mês de competência no formato `yyyy-MM`. */
  month: string;
  requestsByStatus: Partial<Record<RequestStatus, number>>;
  totalRequests: number;
  budgets: CostCenterBudget[];
}

export interface CostCenterBudget {
  costCenterId: number;
  costCenterCode: string;
  costCenterName: string;
  amount: string;
  consumed: string;
  available: string;
}

import { Category } from '../../shared/request-category';
import { RequestStatus } from '../../shared/request-status';

/** Item de `data[]` em `GET /requests`; `total` vem como string decimal. */
export interface PurchaseRequestSummary {
  id: number;
  ownerId: number;
  costCenterId: number;
  status: RequestStatus;
  version: number;
  title: string;
  category: Category;
  total: string;
  neededBy: string | null;
  submittedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CostCenter {
  id: number;
  code: string;
  name: string;
  createdAt: string;
}

import { Category } from '../../shared/request-category';
import { RequestStatus } from '../../shared/request-status';
import { ApprovalStep } from '../requests/requests.models';

/**
 * Item de `GET /approvals/inbox`: o back só devolve o que o usuário pode aprovar agora (mesma `ActionPolicy`
 * do `allowedActions`) e já manda o `lockVersion` para aprovar direto da caixa.
 */
export interface ApprovalInboxItem {
  id: number;
  ownerId: number;
  costCenterId: number;
  status: RequestStatus;
  version: number;
  lockVersion: number;
  title: string;
  category: Category;
  total: string;
  neededBy: string | null;
  currentStepLevel: ApprovalStep['level'];
  submittedAt: string;
  createdAt: string;
  updatedAt: string;
}

import { Role } from '../../core/auth/auth.models';
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

export type RequestAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES' | 'CANCEL' | 'EDIT';

export interface RequestItem {
  description: string;
  quantity: number;
  unitPrice: string;
  subtotal: string;
}

export interface ApprovalStep {
  order: number;
  level: 'NIVEL_1' | 'NIVEL_2' | 'ADMIN';
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
  decidedBy: number | null;
  decidedAt: string | null;
  comment: string | null;
}

/** `GET /requests/:id`: versão atual; `lockVersion` vai de volta no corpo de toda alteração. */
export interface PurchaseRequestDetail {
  id: number;
  ownerId: number;
  costCenterId: number;
  status: RequestStatus;
  version: number;
  lockVersion: number;
  title: string;
  description: string;
  category: Category;
  neededBy: string | null;
  total: string;
  submittedAt: string | null;
  competenceMonth: string | null;
  createdAt: string;
  updatedAt: string;
  items: RequestItem[];
  steps: ApprovalStep[];
  allowedActions: RequestAction[];
}

/** Corpo de `POST /requests`; sem `total`, que o back calcula. `costCenterId` nulo usa o do usuário. */
export interface CreateRequestPayload {
  title: string;
  description: string;
  category: Category;
  costCenterId: number | null;
  neededBy: string | null;
  items: { description: string; quantity: number; unitPrice: string }[];
}

/** Corpo de `PUT /requests/:id`: o centro de custo não muda depois da criação. */
export type UpdateRequestPayload = Omit<CreateRequestPayload, 'costCenterId'> & {
  lockVersion: number;
};

/** Item de `GET /requests/:id/versions`; `submittedAt` nulo é a versão de trabalho ainda em edição. */
export interface RequestVersionSummary {
  version: number;
  current: boolean;
  title: string;
  category: Category;
  total: string;
  submittedAt: string | null;
  competenceMonth: string | null;
  createdAt: string;
}

/** `GET /requests/:id/versions/:n`: conteúdo e cadeia da versão, sem `allowedActions`. */
export interface RequestVersion {
  requestId: number;
  version: number;
  current: boolean;
  title: string;
  description: string;
  category: Category;
  neededBy: string | null;
  total: string;
  submittedAt: string | null;
  competenceMonth: string | null;
  createdAt: string;
  items: RequestItem[];
  steps: ApprovalStep[];
}

export type HistoryAction = 'CREATE' | RequestAction;

/** Evento de `GET /requests/:id/history`; `requestId` é o `X-Request-Id` da chamada que o gerou. */
export interface RequestEvent {
  id: number;
  version: number;
  actorId: number;
  actorRole: Role;
  action: HistoryAction;
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus;
  stepLevel: ApprovalStep['level'] | null;
  comment: string | null;
  occurredAt: string;
  requestId: string | null;
}

/** Ações que viram `POST /requests/:id/<rota>`; `EDIT` é navegação para o formulário. */
export type TransitionAction = Exclude<RequestAction, 'EDIT'>;

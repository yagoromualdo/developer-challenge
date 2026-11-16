export type RequestStatus =
  'DRAFT' | 'PENDING_APPROVAL' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** Mesma ordem do enum do back, que segue o fluxo da solicitação. */
export const REQUEST_STATUSES: readonly RequestStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'CHANGES_REQUESTED',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
];

export const REQUEST_STATUS_LABELS: Record<RequestStatus, string> = {
  DRAFT: 'Rascunho',
  PENDING_APPROVAL: 'Aguardando aprovação',
  CHANGES_REQUESTED: 'Ajustes solicitados',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
};

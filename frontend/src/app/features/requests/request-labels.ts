import { ApprovalStep, HistoryAction } from './requests.models';

export const STEP_LEVEL_LABELS: Record<ApprovalStep['level'], string> = {
  NIVEL_1: 'Nível 1',
  NIVEL_2: 'Nível 2',
  ADMIN: 'Administração',
};

export const STEP_STATUS_LABELS: Record<ApprovalStep['status'], string> = {
  PENDING: 'Pendente',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  CHANGES_REQUESTED: 'Ajustes solicitados',
};

export const HISTORY_ACTION_LABELS: Record<HistoryAction, string> = {
  CREATE: 'Criada',
  EDIT: 'Editada',
  SUBMIT: 'Enviada para aprovação',
  APPROVE: 'Etapa aprovada',
  REJECT: 'Rejeitada',
  REQUEST_CHANGES: 'Ajustes solicitados',
  CANCEL: 'Cancelada',
};

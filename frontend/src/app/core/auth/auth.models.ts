export type Role = 'SOLICITANTE' | 'APROVADOR' | 'ADMIN';

export type ApproverLevel = 'NIVEL_1' | 'NIVEL_2';

export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  role: Role;
  approverLevel: ApproverLevel | null;
  costCenterId: number | null;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: CurrentUser;
}

export interface StoredSession {
  accessToken: string;
  expiresAt: string;
}

export const ROLE_LABELS: Record<Role, string> = {
  SOLICITANTE: 'Solicitante',
  APROVADOR: 'Aprovador',
  ADMIN: 'Administrador',
};

/** Papéis que enxergam a caixa de aprovação; usado na rota e no menu (só UX, quem decide é o back). */
export const APPROVER_ROLES: readonly Role[] = ['APROVADOR', 'ADMIN'];

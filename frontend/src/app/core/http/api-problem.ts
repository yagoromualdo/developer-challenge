import { HttpErrorResponse } from '@angular/common/http';
import { REQUEST_ID_HEADER } from './api';

export interface ApiFieldError {
  field: string;
  message: string;
}

/** Problem Details (RFC 9457) devolvido pelo back, com as extensões `code`, `requestId` e `errors`. */
export interface ApiProblem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  requestId?: string;
  errors?: ApiFieldError[];
}

/** Erro HTTP normalizado pelo `errorInterceptor`; status 0 significa falha de rede. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ApiProblem | null,
    readonly requestId: string | null,
  ) {
    super(problem?.detail ?? `Falha HTTP ${status}`);
    this.name = 'ApiError';
  }

  get code(): string | null {
    return this.problem?.code ?? null;
  }

  get fieldErrors(): ApiFieldError[] {
    return this.problem?.errors ?? [];
  }
}

export function toApiError(response: HttpErrorResponse, sentRequestId: string | null): ApiError {
  const problem = parseProblem(response.error);
  const requestId =
    problem?.requestId ?? response.headers?.get(REQUEST_ID_HEADER) ?? sentRequestId ?? null;
  return new ApiError(response.status, problem, requestId);
}

function parseProblem(body: unknown): ApiProblem | null {
  let candidate = body;
  if (typeof candidate === 'string') {
    try {
      candidate = JSON.parse(candidate);
    } catch {
      return null;
    }
  }
  return isProblem(candidate) ? candidate : null;
}

function isProblem(value: unknown): value is ApiProblem {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { status?: unknown }).status === 'number'
  );
}

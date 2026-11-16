import { HttpParams } from '@angular/common/http';
import { ParamMap, Params } from '@angular/router';
import { CATEGORIES, Category } from '../../shared/request-category';
import { REQUEST_STATUSES, RequestStatus } from '../../shared/request-status';

/** Whitelist de ordenação do back (`RequestSortField`). */
export const SORT_FIELDS = ['createdAt', 'updatedAt', 'status', 'total', 'title'] as const;
export type SortField = (typeof SORT_FIELDS)[number];
export type SortDirection = 'asc' | 'desc';

export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 20, 50, 100];
const MAX_PAGE_SIZE = 100;

/**
 * Busca da listagem. Os query params da tela têm os mesmos nomes e formatos da API
 * (`page` começa em 0, `sort=campo,direção`), então a URL copiada reproduz a mesma chamada.
 */
export interface RequestListQuery {
  status: RequestStatus[];
  category: Category[];
  costCenterId: number | null;
  createdFrom: string | null;
  createdTo: string | null;
  page: number;
  size: number;
  sortField: SortField;
  sortDirection: SortDirection;
}

export const DEFAULT_LIST_QUERY: RequestListQuery = {
  status: [],
  category: [],
  costCenterId: null,
  createdFrom: null,
  createdTo: null,
  page: 0,
  size: 20,
  sortField: 'createdAt',
  sortDirection: 'desc',
};

/** Valores inválidos ou desconhecidos na URL são descartados e caem no padrão. */
export function parseListQuery(params: ParamMap): RequestListQuery {
  const sort = parseSort(params.get('sort'));
  return {
    status: parseMany(params, 'status', REQUEST_STATUSES),
    category: parseMany(params, 'category', CATEGORIES),
    costCenterId: parseInteger(params.get('costCenterId'), 1, Number.MAX_SAFE_INTEGER),
    createdFrom: parseDate(params.get('createdFrom')),
    createdTo: parseDate(params.get('createdTo')),
    page: parseInteger(params.get('page'), 0, Number.MAX_SAFE_INTEGER) ?? DEFAULT_LIST_QUERY.page,
    size: parseInteger(params.get('size'), 1, MAX_PAGE_SIZE) ?? DEFAULT_LIST_QUERY.size,
    sortField: sort?.field ?? DEFAULT_LIST_QUERY.sortField,
    sortDirection: sort?.direction ?? DEFAULT_LIST_QUERY.sortDirection,
  };
}

/** Forma canônica para a URL: omite os padrões para o link ficar curto. */
export function toQueryParams(query: RequestListQuery): Params {
  const params: Params = {};
  if (query.status.length) params['status'] = [...query.status];
  if (query.category.length) params['category'] = [...query.category];
  if (query.costCenterId !== null) params['costCenterId'] = String(query.costCenterId);
  if (query.createdFrom) params['createdFrom'] = query.createdFrom;
  if (query.createdTo) params['createdTo'] = query.createdTo;
  if (query.page !== DEFAULT_LIST_QUERY.page) params['page'] = String(query.page);
  if (query.size !== DEFAULT_LIST_QUERY.size) params['size'] = String(query.size);
  const sort = sortParam(query);
  if (sort !== sortParam(DEFAULT_LIST_QUERY)) params['sort'] = sort;
  return params;
}

export function toHttpParams(query: RequestListQuery): HttpParams {
  let params = new HttpParams()
    .set('page', query.page)
    .set('size', query.size)
    .set('sort', sortParam(query));
  query.status.forEach((status) => (params = params.append('status', status)));
  query.category.forEach((category) => (params = params.append('category', category)));
  if (query.costCenterId !== null) params = params.set('costCenterId', query.costCenterId);
  if (query.createdFrom) params = params.set('createdFrom', query.createdFrom);
  if (query.createdTo) params = params.set('createdTo', query.createdTo);
  return params;
}

export function hasFilters(query: RequestListQuery): boolean {
  return (
    query.status.length > 0 ||
    query.category.length > 0 ||
    query.costCenterId !== null ||
    query.createdFrom !== null ||
    query.createdTo !== null
  );
}

export function sameQuery(a: RequestListQuery, b: RequestListQuery): boolean {
  return paramsKey(toQueryParams(a)) === paramsKey(toQueryParams(b));
}

/** Diz se a URL já está na forma canônica; senão a tela a substitui para refletir a busca feita. */
export function isCanonical(params: ParamMap, query: RequestListQuery): boolean {
  const current: Params = {};
  params.keys.forEach((key) => (current[key] = params.getAll(key)));
  return paramsKey(current) === paramsKey(toQueryParams(query));
}

function paramsKey(params: Params): string {
  return JSON.stringify(
    Object.keys(params)
      .sort()
      .map((key) => [key, ([] as unknown[]).concat(params[key]).map(String)]),
  );
}

function sortParam(query: RequestListQuery): string {
  return `${query.sortField},${query.sortDirection}`;
}

// Aceita repetido (`status=A&status=B`) e separado por vírgula (`status=A,B`), como a API.
function parseMany<T extends string>(params: ParamMap, key: string, allowed: readonly T[]): T[] {
  const requested = new Set(params.getAll(key).flatMap((value) => value.split(',')));
  return allowed.filter((value) => requested.has(value));
}

function parseInteger(value: string | null, min: number, max: number): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  const parsed = Number(value);
  return parsed >= min && parsed <= max ? parsed : null;
}

function parseDate(value: string | null): string | null {
  if (value === null || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value) ? value : null;
}

function parseSort(value: string | null): { field: SortField; direction: SortDirection } | null {
  const [field, direction = 'asc', ...rest] = (value ?? '').split(',').map((part) => part.trim());
  const isField = (SORT_FIELDS as readonly string[]).includes(field);
  if (!isField || rest.length > 0 || (direction !== 'asc' && direction !== 'desc')) {
    return null;
  }
  return { field: field as SortField, direction };
}

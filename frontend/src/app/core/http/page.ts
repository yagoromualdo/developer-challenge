/** Envelope das listagens paginadas do back (`PageResponse`); `page` começa em 0. */
export interface Page<T> {
  data: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

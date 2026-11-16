import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Textos do `mat-paginator` em pt-BR. */
@Injectable()
export class PtBrPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Itens por página';
  override nextPageLabel = 'Próxima página';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primeira página';
  override lastPageLabel = 'Última página';

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    const start = page * pageSize;
    if (length === 0 || start >= length) {
      return `0 de ${length}`;
    }
    return `${start + 1} – ${Math.min(start + pageSize, length)} de ${length}`;
  };
}

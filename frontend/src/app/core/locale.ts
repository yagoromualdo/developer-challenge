import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID, Provider } from '@angular/core';

export const APP_LOCALE = 'pt-BR';

/** Registra os dados do pt-BR e o torna o locale padrão dos pipes do Angular (`DatePipe`). */
export function provideAppLocale(): Provider {
  registerLocaleData(localePt, APP_LOCALE);
  return { provide: LOCALE_ID, useValue: APP_LOCALE };
}

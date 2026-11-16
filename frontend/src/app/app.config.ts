import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { requestIdInterceptor } from './core/http/request-id.interceptor';
import { provideAppLocale } from './core/locale';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppLocale(),
    provideRouter(routes, withComponentInputBinding()),
    // O errorInterceptor vem por último para enxergar o X-Request-Id que foi enviado.
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor, requestIdInterceptor, errorInterceptor]),
    ),
  ],
};

import { Routes } from '@angular/router';
import { APPROVER_ROLES } from './core/auth/auth.models';
import { DEFAULT_ROUTE } from './core/auth/return-url';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { roleGuard } from './core/guards/role.guard';

const placeholder = () =>
  import('./features/placeholder/placeholder-page').then((m) => m.PlaceholderPage);
const requestForm = () =>
  import('./features/requests/request-form-page').then((m) => m.RequestFormPage);

export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: DEFAULT_ROUTE.slice(1) },
      {
        path: 'dashboard',
        title: 'Dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'requests',
        title: 'Solicitações',
        loadComponent: () =>
          import('./features/requests/request-list-page').then((m) => m.RequestListPage),
      },
      {
        path: 'requests/new',
        title: 'Nova solicitação',
        loadComponent: requestForm,
      },
      {
        path: 'requests/:id/edit',
        title: 'Editar solicitação',
        loadComponent: requestForm,
      },
      {
        path: 'requests/:id',
        title: 'Solicitação',
        loadComponent: placeholder,
        data: { heading: 'Detalhe da solicitação' },
      },
      {
        path: 'approvals/inbox',
        title: 'Caixa de aprovação',
        canActivate: [roleGuard],
        loadComponent: placeholder,
        data: { heading: 'Caixa de aprovação', roles: APPROVER_ROLES },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

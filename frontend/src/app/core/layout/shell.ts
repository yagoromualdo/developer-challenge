import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { APPROVER_ROLES, ROLE_LABELS, Role } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';

interface MenuItem {
  label: string;
  path: string;
  roles?: readonly Role[];
}

const MENU: readonly MenuItem[] = [
  { label: 'Dashboard', path: '/dashboard' },
  { label: 'Solicitações', path: '/requests' },
  { label: 'Nova solicitação', path: '/requests/new' },
  { label: 'Caixa de aprovação', path: '/approvals/inbox', roles: APPROVER_ROLES },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.currentUser;
  protected readonly roleLabel = computed(() => {
    const user = this.user();
    return user ? ROLE_LABELS[user.role] : '';
  });
  protected readonly menu = computed(() => {
    const role = this.user()?.role;
    return MENU.filter((item) => !item.roles || (role !== undefined && item.roles.includes(role)));
  });

  protected logout(): void {
    this.auth.clearSession();
    void this.router.navigateByUrl('/login');
  }
}

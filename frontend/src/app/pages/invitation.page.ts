import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, ApiRole, errorMessage } from '../core/api.service';
import { roleLabels } from '../core/session.service';
interface InvitationDetail {
  email: string; displayName: string; firstName: string | null; lastName: string | null;
  role: ApiRole; companyName: string | null; status: string; expiresAt: string;
}
@Component({
  selector: 'app-invitation',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<main class="page"><section class="sheet"><h1 class="page-title">Aceptar invitación</h1>' +
    '@if (loading()) { <p role="status">Consultando invitación…</p> }' +
    '@if (error()) { <p class="err" role="alert">{{ error() }}</p> }' +
    '@if (completed()) { <p role="status">Tu cuenta está lista.</p><a class="btn btn--primary" routerLink="/login">Ingresar</a> }' +
    '@else if (detail(); as invitation) {' +
      '<p>{{ invitation.email }} / {{ roles[invitation.role] }} / {{ invitation.companyName }}</p>' +
      '@if (invitation.status === "PENDING") {' +
        '<form (ngSubmit)="accept()" class="form-grid">' +
          '<label class="field"><span>Nombres</span><input class="input" name="firstName" [(ngModel)]="firstName" required autocomplete="given-name" /></label>' +
          '<label class="field"><span>Apellidos</span><input class="input" name="lastName" [(ngModel)]="lastName" required autocomplete="family-name" /></label>' +
          '<label class="field"><span>Contraseña</span><input class="input" name="password" type="password" [(ngModel)]="password" required autocomplete="new-password" /></label>' +
          '<label class="field"><span>Confirmar contraseña</span><input class="input" name="confirmation" type="password" [(ngModel)]="confirmation" required autocomplete="new-password" /></label>' +
          '<button class="btn btn--primary" type="submit" [disabled]="busy()">{{ busy() ? "Creando cuenta…" : "Crear cuenta" }}</button>' +
        '</form>' +
      '} @else { <p>Esta invitación ya no está disponible.</p><a routerLink="/login">Ir al login</a> }' +
    '}</section></main>',
})
export class InvitationPage {
  private readonly api = inject(ApiService);
  readonly token = input.required<string>();
  readonly detail = signal<InvitationDetail | null>(null);
  readonly error = signal('');
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly completed = signal(false);
  readonly roles = roleLabels;
  firstName = ''; lastName = ''; password = ''; confirmation = '';
  async ngOnInit() {
    try {
      const detail = await this.api.request<InvitationDetail>('GET', '/invitations/' + encodeURIComponent(this.token()));
      this.detail.set(detail); this.firstName = detail.firstName ?? ''; this.lastName = detail.lastName ?? '';
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.loading.set(false); }
  }
  async accept() {
    if (this.busy()) return;
    this.busy.set(true); this.error.set('');
    try {
      await this.api.request('POST', '/invitations/' + encodeURIComponent(this.token()) + '/accept',
        { firstName: this.firstName, lastName: this.lastName, password: this.password, passwordConfirmation: this.confirmation });
      this.password = this.confirmation = ''; this.completed.set(true);
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
}

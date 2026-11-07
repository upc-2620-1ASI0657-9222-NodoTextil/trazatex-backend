import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../core/session.service';
import { StoreService } from '../core/store.service';
import { SHARED } from '../shared/ui';

@Component({
  selector: 'app-perfil',
  imports: [...SHARED],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let u = session.usuario();
    <div class="page page--perfil">
      <ng-template appHeaderActions><button class="btn btn--ghost" type="button" (click)="salir()"><app-icon name="logout" [size]="16" /> Cerrar sesión</button></ng-template>

      <div class="grid-2">
        <section class="sheet">
          <div class="sheet-head"><h2 class="sheet-title">Datos del usuario</h2></div>
          <dl class="dl">
            <dt>Nombres</dt><dd>{{ u.nombres }}</dd>
            <dt>Apellidos</dt><dd>{{ u.apellidos }}</dd>
            <dt>Correo</dt><dd>{{ u.correo }}</dd>
            <dt>Cargo</dt><dd>{{ u.cargo }}</dd>
            <dt>Rol</dt><dd>{{ u.rol }}</dd>
            <dt>Acceso</dt><dd><app-badge [text]="u.estado" [tone]="u.estado === 'Activo' ? 'ok' : 'hist'" /></dd>
          </dl>
        </section>
        <section class="sheet">
          @if (empresa(); as e) {
            <div class="sheet-head"><h2 class="sheet-title">Empresa</h2></div>
            <dl class="dl">
              <dt>Razón social</dt><dd>{{ e.razonSocial }}</dd>
              <dt>RUC</dt><dd class="num">{{ e.ruc }}</dd>
              <dt>Actividades</dt><dd>{{ e.actividades.join(' / ') }}</dd>
              <dt>Estado</dt><dd><app-badge [text]="e.estado" [tone]="e.estado === 'Activa' ? 'ok' : 'hist'" /></dd>
            </dl>
          } @else if (session.esDueno()) {
            <div class="sheet-head"><h2 class="sheet-title">Alcance</h2></div>
            <dl class="dl">
              <dt>Licencia</dt><dd>Perpetua</dd>
              <dt>Empresas</dt><dd class="num">{{ store.empresas().length }} de {{ store.licencia().limiteEmpresas }}</dd>
              <dt>Vista</dt><dd>Administración de empresas de la licencia</dd>
            </dl>
          } @else {
            <div class="sheet-head"><h2 class="sheet-title">Empresa</h2></div>
            <dl class="dl"><dt>Empresa</dt><dd>{{ store.nombreEmpresa(session.empresaId()) }}</dd><dt>Identificador</dt><dd>{{ session.empresaId() }}</dd></dl>
          }
          <hr class="seam" />
          <p class="small muted">Los datos de tu perfil corresponden a tu sesión autenticada.</p>
        </section>
      </div>
    </div>
  `,
})
export class PerfilPage {
  readonly session = inject(SessionService);
  readonly store = inject(StoreService);
  private readonly router = inject(Router);
  readonly empresa = computed(() => this.store.empresa(this.session.empresaId()));
  salir() {
    this.session.logout();
    this.router.navigateByUrl('/login');
  }
}




import { formDraft } from '../shared/form-draft.directive';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionService } from '../core/session.service';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../shared/density';
import { SHARED, UiService } from '../shared/ui';

type Tab = 'usuarios' | 'invitaciones' | 'solicitudes';

@Component({
  selector: 'app-usuarios',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--usuarios">
      <ng-template appHeaderActions>@if (session.esAdmin()) {
            <button class="btn btn--ghost" type="button" (click)="abrirSolicitud()">
              <app-icon name="user" [size]="16" /> Solicitar administrador
            </button>
            <app-action
              cls="btn--primary"
              icon="plus"
              [tipRight]="true"
              [reason]="lleno() ? 'La empresa alcanzó el límite de usuarios de la licencia.' : null"
              (run)="abrirInvitar()"
              >Invitar Operario</app-action
            >
          }</ng-template>

      <app-subnav panel="usuarios"
        [items]="vistas()"
        [(active)]="tab"
        (activeChange)="page.set(0)"
        label="Usuarios"
      />
      <div appTabPanel="usuarios" [tabActive]="tab()">

      <div class="filters">
        <label class="search">
          <app-icon name="search" [size]="16" />
          <input
            class="input"
            placeholder="Buscar nombre o correo"
            [ngModel]="q()"
            (ngModelChange)="q.set($event); page.set(0)"
            aria-label="Buscar"
          />
        </label>
        @if (session.esDueno()) {
          <button class="btn btn--ghost" type="button" (click)="verFiltros.set(true)">
            <app-icon name="filter" [size]="16" /> Filtros
          </button>
        }
        @if (!session.esDueno()) {
          <span class="cap-line">
            <span class="label">Capacidad</span>
            <span class="meter"
              ><i
                [style.width.%]="(capUsados() / (capLimite() || 1)) * 100"
                [class.warn]="capUsados() / (capLimite() || 1) >= 0.8"
              ></i
            ></span>
            <b class="num" title="Usuarios activos e invitaciones pendientes vigentes">{{ capUsados() }} / {{ capLimite() || '—' }}</b>
          </span>
        }
      </div>

      @switch (tab()) {
        @case ('usuarios') {
          @if (usuarios().length) {
            <div class="rows">
              @for (u of paginar(usuarios()); track u.id) {
                <button
                  type="button"
                  class="row-card {{ u.estado === 'Activo' ? 'row-card--ok' : 'row-card--hist' }}"
                  [class.is-dim]="u.estado === 'Inactivo'"
                  (click)="selUser.set(u.id)"
                >
                  <span class="rc-id">{{ store.rolLabel(u.rol) }}</span>
                  <app-icon class="rc-figure" name="user" [size]="44" />
                  <span class="rc-state">{{ u.estado }}</span>
                  <span class="rc-title">{{ u.nombres }} {{ u.apellidos }}</span>
                  <span class="rc-meta"
                    >{{ u.cargo }}
                    @if (session.esDueno()) {
                      / {{ store.nombreEmpresa(u.empresaId) }}
                    }
                  </span>
                </button>
              }
            </div>
            <app-pager [total]="usuarios().length" [(page)]="page" [(size)]="size" />
          } @else if (store.loading()) {
            <app-skeleton />
          } @else {
            <div class="empty"><strong>Sin usuarios</strong></div>
          }
        }
        @case ('invitaciones') {
          <p class="page-lead">Invitaciones que emitiste. Puedes cancelar las que siguen pendientes.</p>
          @if (invitaciones().length) {
            <div class="rows">
              @for (i of paginar(invitaciones()); track i.id) {
                <div
                  class="row-card {{
                    i.estado === 'Aceptada'
                      ? 'row-card--ok'
                      : i.estado === 'Pendiente'
                        ? 'row-card--warn'
                        : 'row-card--hist'
                  }}"
                  style="cursor:default"
                >
                  <span class="rc-id">{{ store.referencia(i.id) }}</span>
                  <span class="rc-state">{{ i.estado }}</span>
                  <span class="rc-title">{{ i.nombre }}</span>
                  <span class="rc-meta"
                    >{{ i.correo }} / expira
                    <span class="num">{{ i.expira | fecha: false }}</span></span
                  >
                  @if ((session.esAdmin() || session.esDueno()) && i.estado === 'Pendiente') {
                    <span class="rc-act"
                      ><button
                        class="btn btn--danger btn--sm"
                        type="button"
                        (click)="cancelar(i.id)"
                      >
                        Cancelar invitación
                      </button></span
                    >
                  }
                </div>
              }
            </div>
            <app-pager [total]="invitaciones().length" [(page)]="page" [(size)]="size" />
          } @else if (store.loading()) {
            <app-skeleton />
          } @else {
            <div class="empty"><strong>Sin invitaciones</strong></div>
          }
        }
        @case ('solicitudes') {
          @if (solicitudes().length) {
            <div class="rows">
              @for (s of paginar(solicitudes()); track s.id) {
                <div
                  class="row-card {{
                    s.estado === 'Aprobada'
                      ? 'row-card--ok'
                      : s.estado === 'Pendiente'
                        ? 'row-card--warn'
                        : 'row-card--fail'
                  }}"
                  style="cursor:default"
                >
                  <span class="rc-id">{{ store.referencia(s.id) }}</span>
                  <span class="rc-state">{{ s.estado }}</span>
                  <span class="rc-title">{{ s.nombre }}</span>
                  <span class="rc-meta"
                    >{{ s.cargo }} / {{ store.nombreEmpresa(s.empresaId) }}</span
                  >
                  @if (session.esDueno() && s.estado === 'Pendiente') {
                    <span class="rc-act">
                      <button
                        class="btn btn--danger btn--sm"
                        type="button"
                        (click)="resolver(s.id, false)"
                      >
                        Rechazar
                      </button>
                      <app-action
                        cls="btn--primary btn--sm"
                        icon="check"
                        [tipRight]="true"
                        [reason]="
                          store.userSlotCount(s.empresaId) >=
                          store.licencia().limiteUsuariosPorEmpresa
                            ? 'La empresa no tiene capacidad disponible.'
                            : null
                        "
                        (run)="resolver(s.id, true)"
                        >Aprobar</app-action
                      >
                    </span>
                  }
                </div>
              }
            </div>
            <app-pager [total]="solicitudes().length" [(page)]="page" [(size)]="size" />
          } @else if (store.loading()) {
            <app-skeleton />
          } @else {
            <div class="empty"><strong>Sin solicitudes</strong></div>
          }
        }
      }
      </div>
    </div>

    @if (verFiltros()) {
      <app-drawer title="Filtros de usuarios" (closed)="verFiltros.set(false)">
        <label class="field"
          ><span>Empresa</span>
          <select
            class="select"
            [ngModel]="fEmpresa()"
            (ngModelChange)="fEmpresa.set($event); page.set(0)"
          >
            <option value="">Todas las empresas</option>
            @for (e of store.empresas(); track e.id) {
              <option [value]="e.id">{{ e.razonSocial }}</option>
            }
          </select>
        </label>
        <div footer class="d-actions">
          <button class="btn btn--primary" type="button" (click)="verFiltros.set(false)">
            Aplicar
          </button>
        </div>
      </app-drawer>
    }
    @if (usuarioSel(); as u) {
      <app-drawer
        [title]="u.nombres + ' ' + u.apellidos"
        [eyebrow]="store.rolLabel(u.rol)"
        [sub]="u.cargo"
        (closed)="selUser.set(null)"
      >
        <app-badge [text]="u.estado" [tone]="u.estado === 'Activo' ? 'ok' : 'hist'" />
        <section class="d-section">
          <h3>Datos</h3>
          <dl class="dl">
            <dt>Nombres</dt>
            <dd>{{ u.nombres }}</dd>
            <dt>Apellidos</dt>
            <dd>{{ u.apellidos }}</dd>
            <dt>Correo</dt>
            <dd>{{ u.correo }}</dd>
            <dt>Cargo</dt>
            <dd>{{ u.cargo }}</dd>
            <dt>Empresa</dt>
            <dd>{{ store.nombreEmpresa(u.empresaId) }}</dd>
          </dl>
        </section>
        @if (session.esAdmin()) {
          <div footer class="d-actions">
            <app-action
              cls="btn--primary btn--sm"
              icon="power"
              [tipRight]="true"
              [reason]="
                u.rol === 'Administrador de Empresa' && u.estado === 'Activo' && administradoresActivos() <= 1
                  ? 'Debe permanecer al menos un administrador activo en la empresa.' : null
              "
              (run)="toggle(u.id, u.estado)"
              >{{ u.estado === 'Activo' ? 'Inactivar acceso' : 'Activar acceso' }}</app-action
            >
          </div>
        }
      </app-drawer>
    }

    @if (invitar()) {
      <app-dialog [appDraft]="inviteDraft"
        title="Invitar Operario"
        [eyebrow]="store.nombreEmpresa(session.empresaId())"
        (closed)="invitar.set(false)"
      >
        <div class="form-grid">
          <label class="field span-all"
            ><span>Nombre del invitado</span><input class="input" [(ngModel)]="fNombre"
          /></label>
          <label class="field span-all"
            ><span>Correo</span><input class="input" type="email" [(ngModel)]="fCorreo"
          /></label>
          <label class="field span-all"
            ><span>Cargo</span><input class="input" [(ngModel)]="fCargo"
          /></label>
        </div>
        @if (error()) {
          <p class="err" role="alert" style="margin-top:.5rem">{{ error() }}</p>
        }
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="invitar.set(false)">
            Cancelar
          </button>
          <button class="btn btn--primary" type="button" (click)="enviarInvitacion()">
            Enviar invitación
          </button>
        </ng-container>
      </app-dialog>
    }
    @if (solicitar()) {
      <app-dialog [appDraft]="requestDraft"
        title="Solicitar otro Administrador"
        eyebrow="Requiere aprobación del Dueño de Licencia"
        (closed)="solicitar.set(false)"
      >
        <div class="form-grid">
          <label class="field span-all"
            ><span>Nombre</span><input class="input" [(ngModel)]="fNombre"
          /></label>
          <label class="field"
            ><span>Correo</span><input class="input" type="email" [(ngModel)]="fCorreo"
          /></label>
          <label class="field"
            ><span>Cargo</span><input class="input" [(ngModel)]="fCargo"
          /></label>
        </div>
        @if (error()) {
          <p class="err" role="alert" style="margin-top:.5rem">{{ error() }}</p>
        }
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="solicitar.set(false)">
            Cancelar
          </button>
          <button class="btn btn--primary" type="button" (click)="enviarSolicitud()">
            Enviar solicitud
          </button>
        </ng-container>
      </app-dialog>
    }
  `,
  styles: `
    .cap-line {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      margin-left: auto;
      min-width: 14rem;
    }
    .cap-line .meter {
      flex: 1;
      min-width: 6rem;
    }
    .rc-act {
      grid-column: 1 / -1;
      display: flex;
      justify-content: flex-end;
      gap: 0.4rem;
      margin-top: 0.35rem;
    }
  `,
})
export class UsuariosPage {
  readonly requestedRequest = input<string | undefined>(undefined, { alias: 'solicitud' });
  constructor() {
    effect(() => {
      const id = this.requestedRequest();
      const request = this.store.solicitudes().find(item => item.id === id);
      if (request) {
        this.tab.set('solicitudes');
        this.q.set(request.correo);
        this.page.set(0);
      }
    });
  }
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  readonly tab = signal<Tab>(this.session.esDueno() ? 'solicitudes' : 'usuarios');
  readonly q = signal('');
  readonly verFiltros = signal(false);
  readonly fEmpresa = signal('');
  readonly page = signal(0);
  readonly size = signal(6);
  readonly selUser = signal<string | null>(null);
  readonly usuarioSel = computed(() => this.store.usuario(this.selUser() ?? undefined) ?? null);
  readonly invitar = signal(false);
  readonly solicitar = signal(false);
  readonly error = signal('');
  fNombre = '';
  fCorreo = '';
  fCargo = '';

  private alcance = (empresaId?: string) =>
    this.session.esDueno()
      ? !this.fEmpresa() || empresaId === this.fEmpresa()
      : empresaId === this.session.empresaId();
  private texto = (s: string) =>
    !this.q().trim() || s.toLowerCase().includes(this.q().trim().toLowerCase());
  readonly usuarios = computed(() =>
    this.store
      .usuarios()
      .filter(
        (u) =>
          u.rol !== 'Dueño de Licencia' &&
          this.alcance(u.empresaId) &&
          this.texto(`${u.nombres} ${u.apellidos} ${u.correo}`),
      ),
  );
  readonly administradoresActivos = computed(() => this.store.usuarios().filter(u =>
    u.empresaId === this.session.empresaId() && u.rol === 'Administrador de Empresa' && u.estado === 'Activo').length);
  readonly invitaciones = computed(() =>
    this.store
      .invitaciones()
      .filter((i) => this.alcance(i.empresaId) && this.texto(`${i.nombre} ${i.correo}`)),
  );
  readonly solicitudes = computed(() =>
    this.store
      .solicitudes()
      .filter((s) => this.alcance(s.empresaId) && this.texto(`${s.nombre} ${s.correo}`)),
  );
  readonly vistas = computed<SubnavItem[]>(() =>
    this.session.esDueno()
      ? [{ id: 'solicitudes', label: 'Solicitudes', count: this.solicitudes().length },
          { id: 'invitaciones', label: 'Invitaciones', count: this.invitaciones().length }]
      : [
          { id: 'usuarios', label: 'Usuarios', count: this.usuarios().length },
          { id: 'invitaciones', label: 'Invitaciones', count: this.invitaciones().length },
          {
            id: 'solicitudes',
            label: 'Solicitudes',
            count: this.solicitudes().filter((s) => s.estado === 'Pendiente').length,
          },
        ],
  );
  readonly lleno = computed(
    () =>
      this.store.licencia().limiteUsuariosPorEmpresa > 0 &&
        this.capUsados() >=
        this.store.licencia().limiteUsuariosPorEmpresa,
  );
  readonly capLimite = computed(() => {
    const per = this.store.licencia().limiteUsuariosPorEmpresa;
    return this.session.esDueno() && !this.fEmpresa() ? per * this.store.empresas().length : per;
  });
  readonly capUsados = computed(
      () => !this.session.esDueno() ? (this.store.companyCapacity()?.activeUsers ?? 0) + (this.store.companyCapacity()?.pendingInvitations ?? 0) :
        this.store
        .usuarios()
        .filter(
          (u) =>
            u.empresaId &&
            (this.session.esDueno()
              ? !this.fEmpresa() || u.empresaId === this.fEmpresa()
              : u.empresaId === this.session.empresaId()),
        ).length,
  );
  paginar = <T>(l: T[]) => paginar(l, this.page(), this.size());

  private reset() {
    this.fNombre = this.fCorreo = this.fCargo = '';
    this.error.set('');
  }
  abrirInvitar() {
    this.reset();
    this.invitar.set(true);
  }
  abrirSolicitud() {
    this.reset();
    this.solicitar.set(true);
  }
  private readDraft = () => ({ nombre: this.fNombre, correo: this.fCorreo, cargo: this.fCargo });
  private restoreDraft = (value: ReturnType<typeof this.readDraft>) => {
    this.fNombre = value.nombre; this.fCorreo = value.correo; this.fCargo = value.cargo;
  };
  readonly inviteDraft = formDraft('user/invite', this.readDraft, this.restoreDraft);
  readonly requestDraft = formDraft('user/request-admin', this.readDraft, this.restoreDraft);
  async enviarInvitacion() {
    const r = await this.store.invitar({
      nombre: this.fNombre,
      correo: this.fCorreo,
      cargo: this.fCargo,
    });
    if (!r.ok) return this.error.set(r.error);
    await this.inviteDraft.clear();
    this.invitar.set(false);
    this.tab.set('invitaciones');
    this.ui.toast(`Invitación registrada para ${this.fCorreo}`);
  }
  async enviarSolicitud() {
    const r = await this.store.solicitarAdmin({
      nombre: this.fNombre,
      correo: this.fCorreo,
      cargo: this.fCargo,
    });
    if (!r.ok) return this.error.set(r.error);
    await this.requestDraft.clear();
    this.solicitar.set(false);
    this.tab.set('solicitudes');
    this.ui.toast(`Solicitud ${r.id} enviada al Dueño de Licencia`);
  }
  async cancelar(id: string) {
    if (!(await this.ui.confirm('¿Cancelar esta invitación? Su enlace dejará de permitir la creación de una cuenta.'))) return;
    const result = await this.store.cancelarInvitacion(id);
    if (!result.ok) return;
    this.ui.toast('Invitación cancelada');
  }
  async toggle(id: string, estado: 'Activo' | 'Inactivo') {
    if (!(await this.ui.confirm(estado === 'Activo' ? '¿Inactivar este usuario? Perderá el acceso y su sesión dejará de ser válida.' : '¿Activar este usuario para que pueda volver a ingresar?'))) return;
    const result = await this.store.setEstadoUsuario(
      id,
      estado === 'Activo' ? 'Inactivo' : 'Activo',
    );
    if (!result.ok) return;
    this.ui.toast(`Usuario ${estado === 'Activo' ? 'inactivado' : 'activado'}`);
  }
  async resolver(id: string, aprobar: boolean) {
    if (!(await this.ui.confirm(aprobar ? '¿Aprobar esta solicitud? Se generará una invitación para el nuevo administrador.' : '¿Rechazar esta solicitud? No se creará una invitación para el solicitante.'))) return;
    const result = await this.store.resolverSolicitud(id, aprobar);
    if (!result.ok) return;
    this.ui.toast(`Solicitud ${id} ${aprobar ? 'aprobada' : 'rechazada'}`);
  }
}

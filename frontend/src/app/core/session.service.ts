import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService, ApiUser, LoginResult } from './api.service';
import { DraftStorage } from './draft-storage.service';
import { Rol, Usuario } from './models';

const KEY = 'trazatex.auth';
export const roleLabels = {
  OPERATOR: 'Operario',
  COMPANY_ADMIN: 'Administrador de Empresa',
  LICENSE_OWNER: 'Dueño de Licencia',
} as const;
export function mapUser(user: ApiUser): Usuario {
  return {
    id: user.id,
    nombres: user.firstName,
    apellidos: user.lastName,
    correo: user.email,
    cargo: user.position,
    rol: roleLabels[user.role],
    empresaId: user.companyId ?? undefined,
    estado: user.status === 'ACTIVE' ? 'Activo' : 'Inactivo',
  };
}
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly drafts = inject(DraftStorage);
  private readonly state = signal<LoginResult | null>(this.restore());
  readonly loggedIn = computed(
    () => !!this.state() && Date.parse(this.state()!.expiresAt) > Date.now(),
  );
  /** Por qué terminó la sesión sin que la persona la cerrara (401 del Gateway). El login lo muestra y lo borra al entrar. */
  readonly endNotice = signal('');
  readonly token = computed(() => (this.loggedIn() ? this.state()!.token : null));
  readonly rol = computed<Rol>(() =>
    this.state() ? roleLabels[this.state()!.user.role] : 'Operario',
  );
  readonly empresaId = computed(() => this.state()?.user.companyId ?? null);
  readonly esOperario = computed(() => this.rol() === 'Operario');
  readonly esAdmin = computed(() => this.rol() === 'Administrador de Empresa');
  readonly esDueno = computed(() => this.rol() === 'Dueño de Licencia');
  readonly usuario = computed<Usuario>(() =>
    this.state()
      ? mapUser(this.state()!.user)
      : {
          id: '',
          nombres: '',
          apellidos: '',
          correo: '',
          cargo: '',
          rol: 'Operario',
          estado: 'Inactivo',
        },
  );
  private expiryTimer?: ReturnType<typeof setTimeout>;
  constructor() {
    this.scheduleExpiry();
  }
  private scheduleExpiry() {
    clearTimeout(this.expiryTimer);
    if (this.state())
      this.expiryTimer = setTimeout(
        () => this.expire(),
        Math.max(0, Date.parse(this.state()!.expiresAt) - Date.now()),
      );
  }
  async login(email: string, password: string): Promise<void> {
    const result = await this.api.request<LoginResult>('POST', '/auth/login', { email, password });
    if (
      !roleLabels[result.user.role] ||
      !result.token ||
      Date.parse(result.expiresAt) <= Date.now()
    ) {
      throw new Error('El servidor devolvió una sesión inválida.');
    }
    this.state.set(result);
    this.endNotice.set('');
    this.scheduleExpiry();
    try {
      sessionStorage.setItem(KEY, JSON.stringify(result));
      localStorage.removeItem('trazatex.session');
    } catch {}
  }
  logout() {
    clearTimeout(this.expiryTimer);
    this.state.set(null);
    try {
      sessionStorage.removeItem(KEY);
      localStorage.removeItem('trazatex.session');
    } catch {}
    // FE-M5: los borradores (con fotos) no sobreviven a la sesión. Si IndexedDB no está disponible no hay nada que borrar.
    void this.drafts.clearDrafts().catch(() => undefined);
  }
  expire(notice = 'Tu sesión venció. Inicia sesión de nuevo.') {
    this.logout();
    this.endNotice.set(notice);
    void this.router.navigateByUrl('/login');
  }
  private restore(): LoginResult | null {
    try {
      const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as LoginResult | null;
      return saved?.token &&
        saved.user?.status === 'ACTIVE' &&
        roleLabels[saved.user.role] &&
        Date.parse(saved.expiresAt) > Date.now()
        ? saved
        : null;
    } catch {
      return null;
    }
  }
}

import { Injectable, computed, inject, signal } from '@angular/core';
import { effect } from '@angular/core';
import { ApiPage, ApiService, ApiUser, errorCode, errorMessage } from './api.service';
import { DraftStorage } from './draft-storage.service';
import { HttpErrorResponse } from '@angular/common/http';
import * as A from './api-models';
import * as C from './api-contracts';
import { mapUser } from './session.service';
import { UiService } from '../shared/ui';
import {
  CampoConfigurable,
  Componente,
  ControlCalidad,
  DatosComerciales,
  Division,
  Empresa,
  EstadoMaquinaria,
  Evidencia,
  Falla,
  Invitacion,
  Lote,
  Maquinaria,
  Notificacion,
  Proceso,
  Prueba,
  Rol,
  SolicitudAdmin,
  TipoMaterial,
  Transferencia,
  Usuario,
  Visibilidad,
  tipoDeNotificacion,
} from './models';
import { SessionService } from './session.service';

/** Texto neutro que sustituye al `name` de un lote que no es de la empresa que consulta. */
export const NOMBRE_OCULTO = '—';

export function ahora(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export interface Disponibilidad {
  bloqueado: boolean;
  etiqueta: string;
  motivo: string;
  tono: 'ok' | 'warn' | 'fail' | 'hist' | 'busy';
}

/** Consulta de la bandeja: `type` es un `CodigoNotificacion` (vacío = todos) y `page` empieza en 0. */
export interface NotificationQuery { unread: boolean; type: string; page: number; size: number }

export type Resultado = { ok: true; id?: string } | { ok: false; error: string };

@Injectable({ providedIn: 'root' })
export class StoreService {
  private readonly session = inject(SessionService);

  readonly empresas = signal<Empresa[]>([]);
  readonly licencia = signal({
    tipo: 'Perpetua' as const,
    limiteEmpresas: 0,
    limiteUsuariosPorEmpresa: 0,
  });
  readonly usuarios = signal<Usuario[]>([]);
  readonly maquinarias = signal<Maquinaria[]>([]);
  readonly lotes = signal<Lote[]>([]);
  readonly divisiones = signal<Division[]>([]);
  readonly procesos = signal<Proceso[]>([]);
  readonly transferencias = signal<Transferencia[]>([]);
  readonly controles = signal<ControlCalidad[]>([]);
  readonly fallas = signal<Falla[]>([]);
  readonly invitaciones = signal<Invitacion[]>([]);
  readonly solicitudes = signal<SolicitudAdmin[]>([]);
  readonly privacidad = signal<Record<string, Partial<Record<CampoConfigurable, Visibilidad>>>>({});
  readonly notificaciones = signal<Notificacion[]>([]);

  empresa = (id?: string | null) => this.empresas().find((e) => e.id === id);
  nombreEmpresa = (id?: string | null) =>
    this.empresa(id)?.razonSocial ?? this.companyNames()[id ?? ''] ?? (id ? 'Empresa ' + this.referencia(id) : '—');
  referencia(id: string): string {
    if (!/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(id)) return id;
    const ids = [...this.procesos().map(p => p.id), ...this.transferencias().map(t => t.id), ...this.controles().map(c => c.id), ...Object.keys(this.companyNames()), ...this.usuarios().map(u => u.id), ...this.solicitudes().map(s => s.id), ...this.invitaciones().map(i => i.id)];
    let size = 8;
    while (size < id.length && ids.some(other => other !== id && other.startsWith(id.slice(0, size)))) size += 2;
    return id.slice(0, size).toUpperCase();
  }
  codigoLote(id: string): string { return this.batchCode(id); }
  /** Título legible de un control: «Evaluación · nombre del lote». El UUID largo no es un título; su forma corta va en `title`. */
  tituloControl(control: Pick<ControlCalidad, 'tipo' | 'loteId'>): string {
    return control.tipo + ' · ' + this.nombreLote(control.loteId);
  }
  /** Texto de `title`/`aria-label` con el id corto del control. */
  refControl(control: Pick<ControlCalidad, 'id'>): string { return 'Control ' + this.referencia(control.id); }
  /** Texto de `title`/`aria-label` con el id corto de la transferencia. */
  refTransferencia(id: string): string { return 'Transferencia ' + this.referencia(id); }
  /** `name` es un dato OWN (ADR-006, decisión 2026-11-03): solo la empresa dueña lo ve. */
  esPropio(lote: Pick<Lote, 'empresaId'> | undefined): boolean {
    const empresa = this.session.empresaId();
    return !!lote && !!empresa && lote.empresaId === empresa;
  }
  /** Nombre libre del lote; en un lote ajeno o desconocido, el texto neutro «—». */
  nombreLote(id: string): string {
    const lote = this.lote(this.batchCode(id));
    return this.esPropio(lote) ? (lote?.nombre ?? this.referencia(this.batchCode(id))) : NOMBRE_OCULTO;
  }
  /**
   * Única excepción a la regla anterior: `GET /api/transfers/{id}` entrega `name` a la empresa origen y a la destino
   * (decisión 2026-11-03). Sin ese detalle cargado se muestra el Traceability ID, que no es privado.
   */
  nombreEnTransferencia(id: string): string {
    const code = this.batchCode(id);
    return this.transferSummaries()[code]?.name ?? this.nombreVisible(code);
  }
  private nombreVisible(code: string): string {
    const lote = this.lote(code);
    return this.esPropio(lote) ? (lote?.nombre ?? this.referencia(code)) : this.referencia(code);
  }
  nombreProceso(p: Proceso): string {
    const material = p.entradas.map(e => this.lote(e.loteId)?.material).find(Boolean);
    return p.tipo + (this.esMezcla(p) ? ' con mezcla' : '') + (material ? ' de ' + material.toLowerCase() : '');
  }
  usuario = (id?: string) => this.usuarios().find((u) => u.id === id);
  nombreUsuario = (id?: string) => {
    const u = this.usuario(id);
    return u ? `${u.nombres} ${u.apellidos}` : '—';
  };
  /**
   * Responsable de un proceso DE LA PROPIA EMPRESA (llamar solo tras comprobar `puedeVerPrivado`). `GET /users` es solo del administrador,
   * así que el operario no tiene a sus compañeros: se muestra su propio nombre o «Otro miembro de tu empresa», nunca «—» (FE-M14).
   */
  nombreResponsable = (id?: string) => {
    const u = this.usuario(id);
    if (u) return `${u.nombres} ${u.apellidos}`;
    const yo = this.session.usuario();
    if (id && id === yo.id) return `${yo.nombres} ${yo.apellidos}`;
    return id && !this.session.esAdmin() ? 'Otro miembro de tu empresa' : '—';
  };
  lote = (id?: string | null) => this.lotes().find((l) => l.id === id);
  maquina = (codigo?: string) => this.maquinarias().find((m) => m.codigo === codigo);
  nombreMaquinaria = (process: Proceso) =>
    this.maquina(process.maquinariaId)?.nombre ?? process.sharedMachineName;
  proceso = (id?: string | null) => this.procesos().find((p) => p.id === id);
  control = (id?: string | null) => this.controles().find((c) => c.id === id);
  falla = (id?: string | null) => this.fallas().find((f) => f.id === id);
  transferencia = (id?: string | null) => this.transferencias().find((t) => t.id === id);

  enAlcance = (empresaId: string) =>
    !this.session.esDueno() && empresaId === this.session.empresaId();

  readonly misLotes = computed(() => this.lotes().filter((l) => this.enAlcance(l.empresaId)));
  readonly misProcesos = computed(() => this.procesos().filter((p) => this.enAlcance(p.empresaId)));
  readonly misControles = computed(() =>
    this.controles().filter((c) => this.enAlcance(c.empresaId)),
  );
  readonly misFallas = computed(() => this.fallas().filter((f) => this.enAlcance(f.empresaId)));
  readonly misTransferencias = computed(() =>
    this.transferencias().filter((t) => this.enAlcance(t.origenId) || this.enAlcance(t.destinoId)),
  );
  /** Filtro y página de la bandeja que se pide al servidor; `refreshNotifications()` (sondeo, marcar leída) repite la última consulta. */
  private notificationQuery: NotificationQuery = { unread: false, type: '', page: 0, size: 6 };
  readonly notificacionesTotal = signal(0);
  /** Total sin filtrar, mientras se haya consultado así; `null` si aún no se sabe. */
  readonly notificacionesTodas = signal<number | null>(null);
  readonly misNotificaciones = computed(() => {
    const rol = this.session.rol();
    const emp = this.session.empresaId();
    return this.notificaciones()
      .filter((n) => n.para === rol && (rol === 'Dueño de Licencia' || n.empresaId === emp))
      .sort((a, b) => b.fecha.localeCompare(a.fecha));
  });
  /** Las no leídas según el servidor (no las de la página cargada). */
  readonly noLeidas = computed(() => this.unreadCount());

  totalEntrada = (p: Proceso) => p.entradas.reduce((s, e) => s + e.cantidadKg, 0);
  totalSalida = (p: Proceso) =>
    p.salidasIds.reduce((s, id) => s + (this.lote(id)?.cantidadKg ?? 0), 0);
  merma = (p: Proceso) => p.mermaKg;
  esMezcla = (p: Proceso) => p.entradas.length > 1;
  duracionH = (inicio: string, fin?: string) =>
    fin ? (Date.parse(fin) - Date.parse(inicio)) / 3_600_000 : undefined;

  controlActivo = (loteId: string) => this.controles().find((c) => c.loteId === loteId && !c.fin && c.status !== 'CANCELLED');
  controlesDe = (loteId: string) =>
    this.controles()
      .filter((c) => c.loteId === loteId)
      .sort((a, b) => a.inicio.localeCompare(b.inicio));
  fallasDe = (loteId: string) => this.fallas().filter((f) => f.loteId === loteId);
  fallaAbiertaDe = (loteId: string) => this.fallas().find((f) => f.loteId === loteId && !f.estado);
  transferenciasDe = (loteId: string) =>
    this.transferencias().filter((t) => t.lotesIds.includes(loteId));
  transferenciaPendienteDe = (loteId: string) =>
    this.transferencias().find((t) => t.estado === 'Pendiente' && t.lotesIds.includes(loteId));
  procesoOrigen = (loteId: string) => this.procesos().find((p) => p.salidasIds.includes(loteId));
  divisionOrigen = (loteId: string) =>
    this.divisiones().find((d) => d.lotesHijosIds.includes(loteId));
  procesosConsumidores = (loteId: string) =>
    this.procesos().filter((p) => p.entradas.some((e) => e.loteId === loteId));
  divisionDe = (loteId: string) => this.divisiones().find((d) => d.loteOrigenId === loteId);

  padres = (loteId: string): string[] => {
    const div = this.divisionOrigen(loteId);
    if (div) return [div.loteOrigenId];
    const p = this.procesoOrigen(loteId);
    return p ? p.entradas.map((e) => e.loteId) : [];
  };
  hijos = (loteId: string): string[] => [
    ...(this.divisionDe(loteId)?.lotesHijosIds ?? []),
    ...this.procesosConsumidores(loteId).flatMap((p) => p.salidasIds),
  ];
  ancestros = (loteId: string): Set<string> => this.recorrer(loteId, this.padres);
  descendientes = (loteId: string): Set<string> => this.recorrer(loteId, this.hijos);

  private recorrer(id: string, next: (id: string) => string[]) {
    const out = new Set<string>();
    const stack = [...next(id)];
    while (stack.length) {
      const cur = stack.pop()!;
      if (out.has(cur)) continue;
      out.add(cur);
      stack.push(...next(cur));
    }
    return out;
  }

  esHistorico = (l: Lote) =>
    ['Procesado', 'Dividido', 'Descartado'].includes(l.fase) || l.productoFinal;

  disponibilidad(l: Lote): Disponibilidad {
    if (l.qualityHold)
      return {
        bloqueado: true,
        etiqueta: 'Bloqueo de calidad',
        motivo: 'Este lote está bloqueado por calidad. Revisa su control o la falla asociada antes de continuar.',
        tono: 'fail',
      };
    if (l.fase === 'Descartado')
      return {
        bloqueado: true,
        etiqueta: 'Histórico',
        motivo: 'Lote descartado: no participa en producción.',
        tono: 'hist',
      };
    if (l.fase === 'Procesado' || l.fase === 'Dividido')
      return {
        bloqueado: true,
        etiqueta: 'Histórico',
        motivo: `Lote ${l.fase.toLowerCase()}: se conserva para trazabilidad.`,
        tono: 'hist',
      };
    if (this.controlActivo(l.id))
      return {
        bloqueado: true,
        etiqueta: 'Control en curso',
        motivo: 'Hay un control de calidad activo. Producción temporalmente no disponible.',
        tono: 'busy',
      };
    if (l.calidad === 'Con falla')
      return {
        bloqueado: true,
        etiqueta: 'Bloqueado',
        motivo: 'Calidad Con falla: pendiente de decisión administrativa.',
        tono: 'fail',
      };
    if (l.calidad === 'Por reevaluar')
      return {
        bloqueado: true,
        etiqueta: 'Bloqueado hasta reevaluación',
        motivo: 'El lote espera una Reevaluación de calidad.',
        tono: 'warn',
      };
    if (l.calidad === 'Posible falla derivada')
      return {
        bloqueado: true,
        etiqueta: 'Bloqueo preventivo',
        motivo: 'Relacionado con una falla confirmada en su genealogía.',
        tono: 'warn',
      };
    if (l.fase === 'En transformación')
      return {
        bloqueado: true,
        etiqueta: 'En transformación',
        motivo: 'El lote está siendo usado en un proceso.',
        tono: 'busy',
      };
    if (l.fase === 'En transferencia')
      return {
        bloqueado: true,
        etiqueta: 'En transferencia',
        motivo: 'Esperando la decisión de la empresa destino.',
        tono: 'busy',
      };
    if (l.productoFinal)
      return {
        bloqueado: true,
        etiqueta: 'Producto final',
        motivo: 'Los productos finales no vuelven a producción.',
        tono: 'hist',
      };
    return {
      bloqueado: false,
      etiqueta: 'Disponible',
      motivo: 'Puede dividirse, transformarse o transferirse.',
      tono: 'ok',
    };
  }
  elegible = (l: Lote) => !this.disponibilidad(l).bloqueado;

  puedeMarcarFinal = (l: Lote) => l.productoFinal && l.empresaId === this.session.empresaId();

  visibilidad = (empresaId: string, campo: CampoConfigurable): Visibilidad =>
    this.privacidad()[empresaId]?.[campo] ?? 'Privado';
  puedeVer = (campo: CampoConfigurable, empresaId: string) =>
    empresaId === this.session.empresaId() || !!this.visibleFields()[empresaId]?.includes(campo);

  puedeVerPrivado = (empresaId: string) => empresaId === this.session.empresaId();

  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  readonly loading = signal(false);
  readonly saving = signal(false);
  /** Porcentaje enviado de la subida en curso (0–100), o `null` si lo que se guarda no es una subida. */
  readonly uploadProgress = signal<number | null>(null);
  readonly error = signal('');
  readonly notificationError = signal('');
  /** No leídas según el servidor (`GET /notifications/unread-count`); alimenta el contador del shell en cualquier pantalla. */
  readonly unreadCount = signal(0);
  readonly unreadBadge = computed(() => (this.unreadCount() > 99 ? '99+' : String(this.unreadCount())));
  readonly licenseUsage = signal<A.ApiLicense | null>(null);
  readonly companyCapacity = signal<{ limit: number; activeUsers: number; pendingInvitations: number } | null>(null);
  readonly companyNames = signal<Record<string, string>>({});
  readonly visibleFields = signal<Record<string, CampoConfigurable[]>>({});
  readonly lineageStatus = signal('');
  readonly transferSummaries = signal<
    Record<string, { name: string; quantityKg: number; materialType: string }>
  >({});
  readonly searchedLineageIds = signal<string[]>([]);
  private selectedLineageCode?: string;
  private readonly requestedEvidence = new Set<string>();
  private readonly requestedLineages = new Set<string>();
  private readonly pendingTransferDetails = new Set<string>();
  readonly loadedTransferDetails = signal<string[]>([]);
  private readonly drafts = inject(DraftStorage);
  private readonly pendingQualityBatches = new Set<string>();
  private readonly batchDetails = new Map<string, A.ApiBatch>();
  private readonly batchIds = new Map<string, string>();
  private readonly batchCodes = new Map<string, string>();
  private readonly machineIds = new Map<string, string>();
  constructor() {
    effect((onCleanup) => {
      const token = this.session.token();
      this.clear();
      if (token) void this.refresh();
      const timer = token ? setInterval(() => {
        void this.refreshNotifications().catch(() => {});
        void this.refreshUnreadCount();
        const pending = [...this.lotes(), ...this.procesos(), ...this.controles(), ...this.fallas()]
          .some(item => item.evidencias.some(evidence => evidence.status === 'PENDING'));
        if (pending) void this.loadEvidence().catch(error => this.error.set(errorMessage(error)));
      }, 30000) : undefined;
      const qualityTimer = token ? setInterval(() => {
        if (this.session.esAdmin() && !this.saving() && !this.loading() &&
          (this.controles().some(control => control.status === 'STARTING' || control.finishPending) || this.fallas().some(failure => failure.discardPending))) {
          void this.refreshQuality().catch(error => this.error.set(errorMessage(error)));
        }
      }, 5000) : undefined;
      onCleanup(() => { clearInterval(timer); clearInterval(qualityTimer); });
    });
  }
  private clear() {
    this.pendingQualityBatches.clear();

    this.controles.set([]);
    this.fallas.set([]);
    this.notificaciones.set([]);
    this.notificacionesTotal.set(0);
    this.notificacionesTodas.set(null);
    this.notificationQuery = { unread: false, type: '', page: 0, size: 6 };
    this.unreadCount.set(0);
    this.empresas.set([]);
    this.usuarios.set([]);
    this.lotes.set([]);
    this.procesos.set([]);
    this.maquinarias.set([]);
    this.divisiones.set([]);
    this.transferencias.set([]);
    this.invitaciones.set([]);
    this.solicitudes.set([]);
    this.privacidad.set({});
    this.transferSummaries.set({});
    this.companyNames.set({});
    this.visibleFields.set({});
    this.licenseUsage.set(null);
    this.companyCapacity.set(null);
    this.batchDetails.clear();
    this.requestedEvidence.clear();
    this.requestedLineages.clear();
    this.pendingTransferDetails.clear();
    this.loadedTransferDetails.set([]);
    this.selectedLineageCode = undefined;
    this.batchIds.clear();
    this.batchCodes.clear();
    this.machineIds.clear();
    this.searchedLineageIds.set([]);
  }
  private reverse<T extends string>(map: Record<T, string>, value: string): T {
    const entry = Object.entries(map).find(([, api]) => api === value);
    if (!entry) throw new Error('Valor del servidor no reconocido: ' + value);
    return entry[0] as T;
  }
  private batchId(code: string) {
    return this.batchIds.get(code) ?? code;
  }
  private batchCode(id: string) {
    return this.batchCodes.get(id) ?? id;
  }
  /** `access` solo llega en Traceability y en la búsqueda por QR; sin él se decide por la empresa del lote. */
  private remember(b: A.ApiBatch, access?: 'OWN' | 'SHARED'): Lote {
    this.batchIds.set(b.traceabilityId, b.id);
    this.batchCodes.set(b.id, b.traceabilityId);
    const own = access !== 'SHARED' && this.esPropio({ empresaId: b.companyId });
    return {
      id: b.traceabilityId,
      qr: b.qrCode,
      // Defensa en profundidad: aunque el servidor enviara `name` en un lote ajeno, no se guarda.
      nombre: own ? (b.name ?? b.materialType) : undefined,
      empresaId: b.companyId,
      material: b.materialType as TipoMaterial,
      cantidadKg: b.quantityKg as number,
      composicion: (b.composition ?? []).map((c) => ({ nombre: c.name, porcentaje: c.percentage })),
      fase: this.reverse(C.FASES_API, b.phase),
      calidad: this.reverse(C.CALIDAD_API, b.qualityStatus),
      origen: b.origin ?? '',
      proveedor: b.supplierName,
      caracteristicasRecepcion: b.receptionCharacteristics ?? '',
      fechaRegistro: b.createdAt,
      productoFinal: b.finalProduct,
      qualityHold: b.qualityHold,
      comercial:
        b.commercialData && Object.values(b.commercialData).some((value) => value != null)
          ? {
              distribuidor: b.commercialData.buyerOrDistributor ?? '',
              precioVenta: b.commercialData.salePrice,
              moneda: b.commercialData.currency,
              fechaSalida: b.commercialData.commercialExitDate ?? '',
              referencia: b.commercialData.commercialReference ?? '',
            }
          : undefined,
      evidencias: [],
    };
  }
  private process(p: A.ApiTransformation): Proceso {
    return {
      id: p.id,
      empresaId: p.companyId,
      tipo: this.reverse(C.TRANSFORMACIONES_API, p.type),
      operarioId: p.operatorId,
      maquinariaId:
        this.maquinarias().find((m) => this.machineIds.get(m.codigo) === p.machineId)?.codigo ??
        p.machineId,
      entradas: p.inputs.map((i) => ({
        loteId: this.batchCode(i.batchId),
        cantidadKg: i.quantityKg,
      })),
      salidasIds: p.outputBatchIds.map((id) => this.batchCode(id)),
      inicio: p.startTime,
      fin: p.endTime,
      mermaKg: p.shrinkageKg,
      desperdicioKg: p.wasteKg,
      motivoDesperdicio: p.wasteReason,
      evidencias: [],
    };
  }
  private transfer(t: A.ApiTransfer): Transferencia {
    return {
      id: t.id,
      origenId: t.originCompanyId,
      destinoId: t.targetCompanyId,
      lotesIds: t.batchIds.map((id) => this.batchCode(id)),
      operarioId: t.initiatedBy,
      inicio: t.departureTime,
      recepcion: t.receptionTime,
      rechazo: t.status === 'REJECTED' ? t.resolvedAt : undefined,
      motivoRechazo: t.rejectionReason,
      estado: this.reverse(C.ESTADOS_TRANSFERENCIA_API, t.status),
    };
  }
  private invitation(i: A.ApiInvitation): Invitacion {
    return {
      id: i.id,
      nombre: i.displayName,
      correo: i.email,
      empresaId: i.companyId,
      rol: this.reverse(C.ROLES_API, i.role),
      estado: (
        {
          PENDING: 'Pendiente',
          ACCEPTED: 'Aceptada',
          EXPIRED: 'Expirada',
          CANCELLED: 'Cancelada',
        } as const
      )[i.status],
      enviada: i.createdAt,
      expira: i.expiresAt,
    };
  }
  async refresh(): Promise<void> {
    const token = this.session.token();
    this.loading.set(true);
    this.error.set('');
    try {
      await Promise.all([this.refreshNotifications().catch(() => {}), this.refreshUnreadCount()]);
      if (this.session.esDueno()) {
        const invitations = await this.api.all<A.ApiInvitation>('/invitations');
        if (token !== this.session.token()) return;
        this.invitaciones.set(invitations.map(i => this.invitation(i)));
      }
      if (this.session.esDueno()) {
        const [companies, license, requests] = await Promise.all([
          this.api.request<A.ApiCompany[]>('GET', '/companies'),
          this.api.request<A.ApiLicense>('GET', '/license'),
          this.api.request<A.ApiAdminRequest[]>('GET', '/admin-requests'),
        ]);
        if (token !== this.session.token()) return;
        this.empresas.set(
          companies.map((c) => ({
            id: c.id,
            razonSocial: c.businessName,
            ruc: c.ruc,
            actividades: c.activities.map((a) => this.reverse(C.ACTIVIDADES_API, a)),
            estado: c.status === 'ACTIVE' ? 'Activa' : 'Inactiva',
            correoPrimerAdmin: '',
          })),
        );
        this.licenseUsage.set(license);
        this.licencia.set({
          tipo: 'Perpetua',
          limiteEmpresas: license.maxCompanies,
          limiteUsuariosPorEmpresa: license.maxUsersPerCompany,
        });
        this.setRequests(requests);
        return;
      }
      const summaries = await this.api.all<A.ApiBatchSummary>('/batches');
      const recipients = await this.api.all<{ id: string; businessName: string }>('/transfers/recipients');
      if (token !== this.session.token()) return;
      this.empresas.set(recipients.map(c => ({ id: c.id, razonSocial: c.businessName, ruc: '', actividades: [], estado: 'Activa', correoPrimerAdmin: '' })));
      const batches: A.ApiBatch[] = summaries.map((summary) => ({
        ...this.batchDetails.get(summary.id),
        ...summary,
        companyId: this.session.empresaId()!,
      }));
      const [machines, processes, transfers] = await Promise.all([
        this.api.request<A.ApiMachine[]>('GET', '/machines'),
        this.api.all<A.ApiTransformation>('/transformations'),
        this.api.all<A.ApiTransfer>('/transfers'),
      ]);
      if (token !== this.session.token()) return;
      this.divisiones.set([]);
      this.visibleFields.set({});
      this.requestedLineages.clear();
      this.lotes.set(batches.map((b) => this.remember(b)));
      this.maquinarias.set(
        machines.map((m) => {
          this.machineIds.set(m.internalCode, m.id);
          return {
            codigo: m.internalCode,
            empresaId: m.companyId,
            nombre: m.name,
            tipo: m.type,
            estado: this.reverse(C.ESTADOS_MAQUINARIA_API, m.status),
          };
        }),
      );
      this.procesos.set(processes.map((p) => this.process(p)));
      this.transferencias.set(transfers.map((t) => this.transfer(t)));
      if (this.session.esAdmin()) {
        await this.refreshQuality();
        await this.refreshAdministration();
      }
      const selected = this.selectedLineageCode ?? batches.find(b => b.finalProduct)?.traceabilityId ?? batches[0]?.traceabilityId;
      if (selected && token === this.session.token()) {
        try {
          await this.loadLineage(selected);
          this.selectedLineageCode = selected;
        } catch {
          this.lineageStatus.set(
            'Genealogía en actualización. Vuelve a consultar en unos segundos.',
          );
        }
      }
      if (token === this.session.token()) await this.loadEvidence();
    } catch (error) {
      if (token === this.session.token()) this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  private setRequests(requests: A.ApiAdminRequest[]) {
    this.solicitudes.set(
      requests.map((r) => ({
        id: r.id,
        nombre: r.fullName,
        correo: r.email,
        cargo: r.position,
        empresaId: r.companyId,
        estado: ({ PENDING: 'Pendiente', APPROVED: 'Aprobada', REJECTED: 'Rechazada' } as const)[
          r.status
        ],
        fecha: r.createdAt,
      })),
    );
  }
  private async refreshAdministration() {
    const token = this.session.token();
    const [users, requests, privacy, capacity, invitations] = await Promise.all([
      this.api.request<ApiUser[]>('GET', '/users'),
      this.api.request<A.ApiAdminRequest[]>('GET', '/admin-requests'),
      this.api.request<A.ApiPrivacy>('GET', '/privacy-settings'),
      this.api.request<{ limit: number; activeUsers: number; pendingInvitations: number }>('GET', '/companies/current/capacity'),
      this.api.all<A.ApiInvitation>('/invitations'),
    ]);
    if (token !== this.session.token()) return;
    this.usuarios.set(users.map(mapUser));
    this.companyCapacity.set(capacity);
    this.licencia.update(license => ({ ...license, limiteUsuariosPorEmpresa: capacity.limit }));
    this.setRequests(requests);
    this.setPrivacy(privacy);
    this.invitaciones.set(invitations.map(invitation => this.invitation(invitation)));
  }
  private setPrivacy(p: A.ApiPrivacy) {
    this.privacidad.set({
      [p.companyId]: Object.fromEntries(
        Object.entries(p.fields).map(([field, value]) => [
          this.reverse(C.CAMPOS_PRIVACIDAD_API, field),
          value === 'SHARED' ? 'Compartido' : 'Privado',
        ]),
      ),
    });
  }
  private async mutate<T>(
    method: string,
    path: string,
    body?: unknown,
    id?: (result: T) => string,
  ): Promise<Resultado> {
    if (this.saving()) return { ok: false, error: 'Hay una operación en curso.' };
    this.saving.set(true);
    let fingerprint: string | undefined;
    try {
      const payload = body instanceof FormData ? await Promise.all([...body.entries()].map(async ([key, value]) => [key,
        value instanceof File ? [value.name, value.size, [...new Uint8Array(await crypto.subtle.digest('SHA-256', await value.arrayBuffer()))]] : value])) : body;
      const pending = await this.drafts.requestId(this.session.usuario().id, method, path, payload, body instanceof FormData);
      fingerprint = pending.fingerprint;
      if (body instanceof FormData) this.uploadProgress.set(0);
      const result = await this.api.request<T>(method, path, body, pending.id, body instanceof FormData ? percent => this.uploadProgress.set(percent) : undefined);
      if (!pending.context) await this.drafts.remove(fingerprint);
      try {
        if (path === '/batches' && method === 'POST') {
          const batch = result as A.ApiBatch;
          this.batchDetails.set(batch.id, batch);
          const mapped = this.remember(batch);
          this.lotes.update(items => [...items.filter(item => item.id !== mapped.id), mapped]);
        } else if (/^\/batches\/[^/]+\/split$/.test(path)) {
          const split = result as { parent: A.ApiBatch; children: A.ApiBatch[] };
          const mapped = [split.parent, ...split.children].map(batch => { this.batchDetails.set(batch.id, batch); return this.remember(batch); });
          this.lotes.update(items => [...items.filter(item => !mapped.some(batch => batch.id === item.id)), ...mapped]);
          this.divisiones.update(items => [...items.filter(item => item.loteOrigenId !== split.parent.traceabilityId), { id: 'division-' + split.parent.id, loteOrigenId: split.parent.traceabilityId, lotesHijosIds: split.children.map(batch => batch.traceabilityId), operarioId: this.session.usuario()?.id ?? '', empresaId: split.parent.companyId, fecha: split.children[0].createdAt }]);
          this.requestedLineages.clear();
        } else if (/\/evidence(\/|$)/.test(path)) {
          await this.loadEvidence();
        } else if (path.startsWith('/quality/') && !path.endsWith('/discard')) {
          const [, , resource, resourceId] = path.split('/');
          const code = resource === 'batches' ? this.batchCode(resourceId)
            : resource === 'controls' ? this.control(resourceId)?.loteId
            : resource === 'failures' ? this.falla(resourceId)?.loteId : undefined;
          if (code && this.lote(code)?.empresaId === this.session.empresaId()) {
            this.batchDetails.delete(this.batchId(code));
            await this.loadBatchDetail(code);
          }
          await this.refreshQuality();
          await this.loadEvidence();
        } else if (this.session.esAdmin() && /^\/(invitations|admin-requests|users|privacy-settings)(\/|$)/.test(path)) {
          await this.refreshAdministration();
        } else await this.refresh();
      } catch (error) {
        this.error.set('La operación se guardó, pero no se pudo actualizar la vista. ' + errorMessage(error));
      }
      return { ok: true, id: id?.(result) };
    } catch (error) {
      if (fingerprint && error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500 && ![408, 429].includes(error.status))
        await this.drafts.remove(fingerprint);
      const message = errorMessage(error);
      this.ui.toast(message, true);
      return { ok: false, error: message };
    } finally {
      this.saving.set(false);
      this.uploadProgress.set(null);
    }
  }
  crearLote(d: {
    nombre: string;
    proveedor?: string;
    origen: string;
    material: TipoMaterial;
    composicion: Componente[];
    cantidadKg: number;
    caracteristicasRecepcion: string;
    evidencias: Evidencia[];
  }) {
    return this.mutate<A.ApiBatch>(
      'POST',
      '/batches',
      {
        name: d.nombre,
        materialType: d.material,
        composition: d.composicion.map((c) => ({ name: c.nombre, percentage: c.porcentaje })),
        quantityKg: d.cantidadKg,
        origin: d.origen,
        supplierName: d.proveedor,
        receptionCharacteristics: d.caracteristicasRecepcion,
      },
      (b) => b.traceabilityId,
    );
  }
  dividirLote(id: string, children: { nombre: string; cantidadKg: number }[]) {
    return this.mutate<{ children: A.ApiBatch[] }>(
      'POST',
      '/batches/' + this.batchId(id) + '/split',
      { children: children.map((c) => ({ name: c.nombre, quantityKg: c.cantidadKg })) },
      (r) => r.children.map((b) => b.traceabilityId).join(', '),
    );
  }
  private commercial(c?: DatosComerciales) {
    return c
      ? {
          buyerOrDistributor: c.distribuidor,
          salePrice: c.precioVenta,
          currency: c.moneda,
          commercialExitDate: c.fechaSalida || null,
          commercialReference: c.referencia,
        }
      : {};
  }
  actualizarDatosComerciales(id: string, commercial?: DatosComerciales) {
    this.batchDetails.delete(this.batchId(id));
    return this.mutate(
      'PUT',
      '/batches/' + this.batchId(id) + '/commercial-data',
      this.commercial(commercial),
    );
  }
  iniciarProceso(d: { entradas: string[]; tipo: string; maquinariaId: string }) {
    return this.mutate<A.ApiTransformation>(
      'POST',
      '/transformations',
      {
        type: C.TRANSFORMACIONES_API[d.tipo as keyof typeof C.TRANSFORMACIONES_API],
        machineId: this.machineIds.get(d.maquinariaId),
        batchIds: d.entradas.map((id) => this.batchId(id)),
      },
      (p) => p.id,
    );
  }
  finalizarProceso(
    id: string,
    outputs: {
      nombre: string;
      material: TipoMaterial;
      cantidadKg: number;
      productoFinal: boolean;
      comercial?: DatosComerciales;
    }[],
    _composition: Componente[],
    wasteKg: number,
    wasteReason: string,
  ) {
    return this.mutate<{ outputs: A.ApiBatch[] }>(
      'POST',
      '/transformations/' + id + '/complete',
      {
        outputs: outputs.map((o) => ({
          name: o.nombre,
          materialType: o.material,
          quantityKg: o.cantidadKg,
          finalProduct: o.productoFinal,
          commercialData: this.commercial(o.comercial),
        })),
        wasteKg,
        wasteReason,
      },
      (r) => r.outputs.map((b) => b.traceabilityId).join(', '),
    );
  }
  iniciarTransferencia(ids: string[], targetCompanyId: string) {
    return this.mutate<A.ApiTransfer>(
      'POST',
      '/transfers',
      { targetCompanyId, batchIds: ids.map((id) => this.batchId(id)) },
      (t) => t.id,
    );
  }
  aceptarTransferencia(id: string) {
    return this.mutate('POST', '/transfers/' + id + '/accept');
  }
  rechazarTransferencia(id: string, reason: string) {
    return this.mutate('POST', '/transfers/' + id + '/reject', { reason });
  }
  guardarMaquinaria(m: Maquinaria, original?: string): Promise<Resultado> {
    if (original)
      return Promise.resolve({
        ok: false,
        error: 'El backend permite cambiar el estado; no permite editar los datos de maquinaria.',
      });
    return this.mutate('POST', '/machines', {
      internalCode: m.codigo,
      name: m.nombre,
      type: m.tipo,
    });
  }
  setEstadoMaquinaria(code: string, status: EstadoMaquinaria) {
    return this.mutate('PATCH', '/machines/' + this.machineIds.get(code) + '/status', {
      status: C.ESTADOS_MAQUINARIA_API[status],
    });
  }
  usuariosDe = (id: string) => this.usuarios().filter((u) => u.empresaId === id);
  setEstadoUsuario(id: string, status: Usuario['estado']) {
    return this.mutate('PATCH', '/users/' + id + '/status', {
      status: status === 'Activo' ? 'ACTIVE' : 'INACTIVE',
    });
  }
  invitar(d: { nombre: string; correo: string; cargo?: string }): Promise<Resultado> {
    const [firstName, ...rest] = d.nombre.trim().split(/\s+/);
    return this.mutate<A.ApiInvitation>(
      'POST',
      '/invitations/operators',
      { firstName, lastName: rest.join(' '), email: d.correo, position: d.cargo ?? '' },
      (i) => {
        this.invitaciones.update((items) => [
          ...items.filter((x) => x.id !== i.id),
          this.invitation(i),
        ]);
        return i.id;
      },
    );
  }
  cancelarInvitacion(id: string) {
    return this.mutate<A.ApiInvitation>(
      'POST',
      '/invitations/' + id + '/cancel',
      undefined,
      (invitation) => {
        this.invitaciones.update((items) =>
          items.map((item) => (item.id === invitation.id ? this.invitation(invitation) : item)),
        );
        return invitation.id;
      },
    );
  }
  solicitarAdmin(d: { nombre: string; correo: string; cargo: string }) {
    return this.mutate<A.ApiAdminRequest>(
      'POST',
      '/admin-requests',
      { fullName: d.nombre, email: d.correo, position: d.cargo },
      (r) => r.id,
    );
  }
  resolverSolicitud(id: string, approve: boolean) {
    return this.mutate('PATCH', '/admin-requests/' + id + '/decision', {
      decision: approve ? 'APPROVED' : 'REJECTED',
    });
  }
  crearEmpresa(d: {
    razonSocial: string;
    ruc: string;
    actividades: string[];
    correoPrimerAdmin: string;
  }) {
    return this.mutate<{ company: A.ApiCompany }>(
      'POST',
      '/companies',
      {
        businessName: d.razonSocial,
        ruc: d.ruc,
        activities: d.actividades.map(
          (a) => C.ACTIVIDADES_API[a as keyof typeof C.ACTIVIDADES_API],
        ),
        adminEmail: d.correoPrimerAdmin,
      },
      (r) => r.company.id,
    );
  }
  setEstadoEmpresa(id: string, status: Empresa['estado']) {
    return this.mutate('PATCH', '/companies/' + id + '/status', {
      status: status === 'Activa' ? 'ACTIVE' : 'INACTIVE',
    });
  }
  setVisibilidad(_companyId: string, field: CampoConfigurable, value: Visibilidad) {
    return this.mutate('PUT', '/privacy-settings', {
      fields: { [C.CAMPOS_PRIVACIDAD_API[field]]: value === 'Compartido' ? 'SHARED' : 'PRIVATE' },
    });
  }
  agregarEvidencia(
    type: 'lote' | 'proceso' | 'control' | 'falla',
    id: string,
    file: File,
  ): Promise<Resultado> {
    this.requestedEvidence.add(type + '/' + id);
    const body = new FormData();
    body.append('files', file);
    if (type === 'control' || type === 'falla') {
      body.append(type === 'control' ? 'controlId' : 'failureId', id);
      return this.mutate('POST', '/quality/evidence', body);
    }
    return this.mutate(
      'POST',
      (type === 'lote' ? '/batches/' + this.batchId(id) : '/transformations/' + id) + '/evidence',
      body,
    );
  }
  eliminarEvidencia(type: 'lote' | 'proceso' | 'control' | 'falla', id: string, evidenceId: string): Promise<Resultado> {
    const path = type === 'lote' ? '/batches/' + this.batchId(id) : type === 'proceso' ? '/transformations/' + id : '/quality/' + (type === 'control' ? 'controls/' : 'failures/') + id;
    return this.mutate('DELETE', path + '/evidence/' + evidenceId);
  }
  /** Los campos configurables que la empresa dueña comparte se deducen de cuáles llegan en la vista SHARED. */
  private markVisibleFields(b: Pick<A.VisibleBatch, 'companyId' | 'supplierName' | 'origin' | 'quantityKg' | 'composition' | 'receptionCharacteristics' | 'failureCause' | 'qualityTests'>) {
    const fields: CampoConfigurable[] = [];
    if (b.supplierName !== undefined) fields.push('proveedor');
    if (b.origin !== undefined) fields.push('origen');
    if (b.quantityKg !== undefined) fields.push('cantidad');
    if (b.composition !== undefined) fields.push('composicion');
    if (b.receptionCharacteristics !== undefined) fields.push('caracteristicasRecepcion');
    if (b.failureCause !== undefined) fields.push('causaFalla');
    if (b.qualityTests !== undefined) fields.push('valoresControl');
    this.visibleFields.update((old) => ({ ...old, [b.companyId]: fields }));
  }
  /** Incorpora un lote encontrado por QR o por enlace. Un lote ajeno entra solo con su vista compartida (FE-C1: sin nombre). */
  private acceptLot(batch: A.ApiBatch, access: 'OWN' | 'SHARED', businessName?: string): string {
    const mapped = this.remember(batch, access);
    if (access === 'OWN') {
      this.batchDetails.set(batch.id, batch);
      this.lotes.update(items => items.some(item => item.id === mapped.id)
        ? items.map(item => item.id === mapped.id ? { ...mapped, evidencias: item.evidencias } : item)
        : [...items, mapped]);
      return mapped.id;
    }
    if (businessName) this.companyNames.update(names => ({ ...names, [batch.companyId]: businessName }));
    // Un lote ajeno ya conocido (p. ej. por la genealogía, con más campos compartidos) no se degrada a la vista mínima,
    // ni sus campos visibles tampoco.
    if (!this.lotes().some(item => item.id === mapped.id)) {
      this.markVisibleFields(batch);
      this.lotes.update(items => [...items, mapped]);
    }
    return mapped.id;
  }
  /**
   * Búsqueda por Traceability ID **exacto** (US30, nunca por nombre): el backend la permite también con lotes de otra
   * empresa, y entonces devuelve su vista compartida. Deja registrado el identificador interno del lote.
   */
  private async resolveByTraceabilityId(traceabilityId: string): Promise<string> {
    const page = await this.api.request<ApiPage<A.ApiLineageHit>>('GET', '/traceability/lineages?traceabilityId=' + encodeURIComponent(traceabilityId) + '&size=100');
    const hit = page.items.find(item => item.traceabilityId.toUpperCase() === traceabilityId.toUpperCase());
    if (!hit) throw new Error('El lote no existe o no tienes acceso.');
    if (hit.access === 'OWN') return this.acceptLot(await this.api.request<A.ApiBatch>('GET', '/batches/' + hit.batchId), 'OWN');
    return this.acceptLot({
      id: hit.batchId, traceabilityId: hit.traceabilityId, qrCode: hit.qrCode, companyId: hit.companyId, materialType: hit.materialType,
      phase: hit.phase, qualityStatus: hit.qualityStatus, finalProduct: hit.finalProduct, createdAt: hit.createdAt,
    }, 'SHARED', hit.businessName);
  }
  /**
   * Resuelve un QR leído por la cámara y devuelve el Traceability ID del lote.
   * - Enlace `/lotes/<Traceability ID>`: búsqueda exacta por ese ID (US30), nunca por nombre; el lote puede ser de otra empresa.
   * - Código `qrCode` del sistema: `GET /batches/qr/{qr}` → `{access, batch}`.
   * Un lote de otra empresa entra con su vista compartida (SHARED), no como error.
   */
  async resolveLotQr(value: string): Promise<string> {
    const raw = value.trim();
    if (!raw || raw.length > 2048) throw new Error('El código QR no corresponde a un lote de TrazaTex.');
    if (/^https?:\/\//i.test(raw)) {
      let url: URL;
      try { url = new URL(raw); } catch { throw new Error('El código QR no corresponde a un lote de TrazaTex.'); }
      const match = /^\/lotes\/([A-Za-z0-9-]+)\/?$/.exec(url.pathname);
      if (url.origin !== window.location.origin || !match) throw new Error('El código QR no corresponde a esta plataforma TrazaTex.');
      const traceabilityId = match[1];
      if (/^TT-/i.test(traceabilityId)) return this.resolveByTraceabilityId(traceabilityId);
      // Enlace con el identificador interno del lote: solo un lote propio puede abrirse por él.
      const detail = await this.api.request<A.ApiBatch>('GET', '/batches/' + this.batchId(traceabilityId));
      return this.acceptLot(detail, 'OWN');
    }
    if (!/^[A-Za-z0-9-]{8,128}$/.test(raw)) throw new Error('El código QR no corresponde a un lote de TrazaTex.');
    const lookup = await this.api.request<A.ApiQrLookup>('GET', '/batches/qr/' + encodeURIComponent(raw));
    return this.acceptLot(lookup.batch, lookup.access);
  }
  requestLineage(code: string) {
    if (this.loading() || this.requestedLineages.has(code)) return;
    this.requestedLineages.add(code);
    // Un enlace `/lotes/<Traceability ID>` abierto directamente (cámara del teléfono, `returnUrl` tras iniciar sesión) llega con
    // un lote que quizá no es de la empresa y cuyo identificador interno aún no se conoce: se resuelve antes por su ID exacto.
    const known = this.batchIds.has(code) || !/^TT-/i.test(code);
    void (known ? Promise.resolve() : this.resolveByTraceabilityId(code)).then(() => this.loadLineage(code)).catch(error => {
      if (this.session.token()) this.error.set(errorMessage(error));
      this.requestedLineages.delete(code);
    });
  }
  requestTransferDetail(id: string) {
    if (this.loading() || this.loadedTransferDetails().includes(id) || this.pendingTransferDetails.has(id)) return;
    this.pendingTransferDetails.add(id);
    const token = this.session.token();
    void this.api.request<{ batches: { batchId: string; traceabilityId: string; name: string; quantityKg: number; materialType: string }[] }>('GET', '/transfers/' + id)
      .then(detail => {
        if (token !== this.session.token()) return;
        for (const batch of detail.batches) {
          this.batchIds.set(batch.traceabilityId, batch.batchId);
          this.batchCodes.set(batch.batchId, batch.traceabilityId);
        }
        this.transferSummaries.update(previous => ({ ...previous, ...Object.fromEntries(detail.batches.map(batch => [batch.traceabilityId, batch])) }));
        this.transferencias.update(items => items.map(item => item.id === id ? { ...item, lotesIds: item.lotesIds.map(code => this.batchCode(code)) } : item));
        this.loadedTransferDetails.update(items => [...items, id]);
      }).catch(error => {
        if (token === this.session.token()) this.error.set(errorMessage(error));
      }).finally(() => this.pendingTransferDetails.delete(id));
  }
  requestEvidence(type: 'lote' | 'proceso' | 'control' | 'falla', id: string) {
    const key = type + '/' + id;
    if (this.requestedEvidence.has(key)) return;
    this.requestedEvidence.add(key);
    void this.loadEvidence(key).catch((error) => { this.requestedEvidence.delete(key); this.error.set(errorMessage(error)); });
  }
  private async loadBatchDetail(code: string) {
    const id = this.batchId(code);
    if (this.batchDetails.has(id)) return;
    const token = this.session.token();
    const detail = await this.api.request<A.ApiBatch>('GET', '/batches/' + id);
    if (token !== this.session.token()) return;
    this.batchDetails.set(id, detail);
    const mapped = this.remember(detail);
    this.lotes.update((items) =>
      items.map((batch) =>
        batch.id === mapped.id ? { ...mapped, evidencias: batch.evidencias } : batch,
      ),
    );
  }
  ensureQualityBatch(code: string) {
    if (this.lote(code) || this.pendingQualityBatches.has(code)) return;
    this.pendingQualityBatches.add(code);
    void this.loadLineage(code).catch(error => this.error.set(errorMessage(error)))
      .finally(() => this.pendingQualityBatches.delete(code));
  }
  private async loadEvidence(only?: string) {
    const token = this.session.token();
    const read = async (path: string): Promise<Evidencia[]> => {
      const response = await this.api.request<{ items: A.ApiEvidence[] }>(
        'GET',
        path + '/evidence',
      );
      return response.items.map((e) => ({
        id: e.id,
        nombre: e.originalFilename,
        url: e.url ?? '',
        fecha: e.createdAt,
        status: e.status,
      }));
    };
    for (const batch of this.misLotes().filter((b) => (this.requestedEvidence.has('lote/' + b.id) && (!only || only === 'lote/' + b.id)))) {
      await this.loadBatchDetail(batch.id);
      const items = await read('/batches/' + this.batchId(batch.id));
      if (token !== this.session.token()) return;
      this.lotes.update((list) =>
        list.map((b) => (b.id === batch.id ? { ...b, evidencias: items } : b)),
      );
    }
    for (const process of this.misProcesos().filter((p) =>
      (this.requestedEvidence.has('proceso/' + p.id) && (!only || only === 'proceso/' + p.id)),
    )) {
      for (const input of process.entradas) await this.loadBatchDetail(input.loteId);
      const items = await read('/transformations/' + process.id);
      if (token !== this.session.token()) return;
      this.procesos.update((list) =>
        list.map((p) => (p.id === process.id ? { ...p, evidencias: items } : p)),
      );
    }
    if (this.session.esAdmin()) {
      for (const control of this.misControles().filter(c => (this.requestedEvidence.has('control/' + c.id) && (!only || only === 'control/' + c.id)))) {
        const items = await read('/quality/controls/' + control.id);
        if (token !== this.session.token()) return;
        this.controles.update(list => list.map(c => c.id === control.id ? { ...c, evidencias: items } : c));
      }
      for (const failure of this.misFallas().filter(f => (this.requestedEvidence.has('falla/' + f.id) && (!only || only === 'falla/' + f.id)))) {
        const items = await read('/quality/failures/' + failure.id);
        if (token !== this.session.token()) return;
        this.fallas.update(list => list.map(f => f.id === failure.id ? { ...f, evidencias: items } : f));
      }
    }
  }
  userSlotCount(id: string) {
    return this.licenseUsage()?.companies.find((c) => c.companyId === id)?.usedSlots ?? 0;
  }
  async searchLineages(query: string): Promise<string[]> {
    // La búsqueda por nombre solo opera sobre lotes propios; los ajenos se buscan por Traceability ID o QR exactos.
    const byName = this.misLotes().filter(batch => this.esPropio(batch) && (batch.id.toLocaleLowerCase().startsWith(query.toLocaleLowerCase()) || batch.nombre?.toLocaleLowerCase().includes(query.toLocaleLowerCase())));
    if (byName.length) {
      const selected = byName[0];
      await this.loadLineage(selected.id);
      this.searchedLineageIds.set(byName.map(batch => batch.id));
      return byName.map(batch => batch.id);
    }
    type Match = { batchId: string; traceabilityId: string };
    let matches = await this.api.all<Match>(
      '/traceability/lineages?traceabilityId=' + encodeURIComponent(query),
    );
    if (!matches.length)
      matches = await this.api.all<Match>(
        '/traceability/lineages?qrCode=' + encodeURIComponent(query),
      );
    for (const match of matches) {
      this.batchIds.set(match.traceabilityId, match.batchId);
      this.batchCodes.set(match.batchId, match.traceabilityId);
    }
    if (matches.length) await this.loadLineage(matches[0].traceabilityId);
    this.searchedLineageIds.set(matches.map((m) => m.traceabilityId));
    return matches.map((m) => m.traceabilityId);
  }
  async loadLineage(code: string) {
    this.selectedLineageCode = code;
    const token = this.session.token();
    const read = async (attempt = 0): Promise<A.ApiLineage> => {
      try {
        const graph = await this.api.request<A.ApiLineage>('GET', '/traceability/batches/' + this.batchId(code) + '/lineage');
        const own = this.lote(code)?.empresaId === this.session.empresaId();
        const expectedChildren = this.divisionDe(code)?.lotesHijosIds.length ?? (own && this.lote(code)?.fase === 'Dividido' ? 2 : 0);
        const actualChildren = graph.edges.filter(edge => edge.from === this.batchId(code) && edge.type === 'SPLIT_INTO').length;
        if (own && attempt < 3 && (!graph.complete || actualChildren < expectedChildren)) {
          this.lineageStatus.set('Actualizando la genealogía de la operación recién guardada…');
          await new Promise(resolve => setTimeout(resolve, 500 * (attempt + 1)));
          return read(attempt + 1);
        }
        return graph;
      } catch (error) {
        if (error instanceof HttpErrorResponse && error.status === 404 && this.lote(code)?.empresaId === this.session.empresaId() && attempt < 3) {
          this.lineageStatus.set('Actualizando la genealogía de la operación recién guardada…');
          await new Promise(resolve => setTimeout(resolve, 500 * (attempt + 1)));
          return read(attempt + 1);
        }
        throw error;
      }
    };
    const result = await read();
    if (token !== this.session.token()) return;
    this.lineageStatus.set(
      !result.complete
        ? 'Genealogía en actualización.'
        : result.truncated
          ? 'Se muestran hasta 10 niveles de genealogía.'
          : '',
    );
    const batches = result.nodes.filter((n): n is A.VisibleBatch => n.kind === 'BATCH');
    const names = { ...this.companyNames() };
    for (const n of result.nodes) names[n.companyId] = n.businessName;
    this.companyNames.set(names);
    const foreignCompanies = new Set(
      result.nodes
        .filter((node) => node.companyId !== this.session.empresaId())
        .map((node) => node.companyId),
    );
    this.visibleFields.update((previous) => ({
      ...previous,
      ...Object.fromEntries([...foreignCompanies].map((id) => [id, []])),
    }));
    for (const b of batches) {
      const mapped = { ...this.remember(b, b.access), failureCause: b.failureCause, qualityTests: b.qualityTests };
      this.lotes.update((list) =>
        list.some((x) => x.id === mapped.id)
          ? list.map((existing) =>
              existing.id === mapped.id ? b.access === 'SHARED' ? mapped : { ...existing, failureCause: b.failureCause, qualityTests: b.qualityTests } : existing,
            )
          : [...list, mapped],
      );
      if (b.access === 'SHARED') this.markVisibleFields(b);
    }
    for (const n of result.nodes) {
      if (
        n.kind !== 'TRANSFORMATION'
      )
        continue;
      if (n.companyId !== this.session.empresaId()) {
        const fields: CampoConfigurable[] = [];
        if (n.machineName != null || n.machineType != null) fields.push('maquinaria');
        if (n.shrinkageKg != null) fields.push('merma');
        if (n.wasteKg != null) fields.push('desperdicio');
        if (n.wasteReason != null) fields.push('motivoDesperdicio');
        this.visibleFields.update((previous) => ({
          ...previous,
          [n.companyId]: [...new Set([...(previous[n.companyId] ?? []), ...fields])],
        }));
      }
      const existingProcess = this.proceso(n.id);
      const inputs = result.edges
        .filter((e) => e.to === n.id)
        .map((e) => ({
          loteId: this.batchCode(e.from),
          cantidadKg: this.lote(this.batchCode(e.from))?.cantidadKg as number,
        }));
      const outputs = result.edges.filter((e) => e.from === n.id).map((e) => this.batchCode(e.to));
      this.procesos.update((list) => [
        ...list.filter((process) => process.id !== n.id),
        {
          id: n.id,
          empresaId: n.companyId,
          tipo: this.reverse(C.TRANSFORMACIONES_API, n.type),
          operarioId: this.proceso(n.id)?.operarioId ?? '',
          maquinariaId: this.proceso(n.id)?.maquinariaId ?? '',
          sharedMachineName: n.machineName,
          inicio: n.startedAt,
          fin: n.completedAt,
          entradas: [...new Map([...(existingProcess?.entradas ?? []).map(input => ({ ...input, loteId: this.batchCode(input.loteId) })), ...inputs].map(input => [input.loteId, input])).values()],
          salidasIds: [...new Set([...(existingProcess?.salidasIds ?? []).map(id => this.batchCode(id)), ...outputs])],
          mermaKg: n.shrinkageKg,
          desperdicioKg: n.wasteKg,
          motivoDesperdicio: n.wasteReason,
          evidencias: existingProcess?.evidencias ?? [],
        },
      ]);
    }
    for (const edge of result.edges) {
      if (edge.type !== 'SPLIT_INTO') continue;
      if (!batches.some((b) => b.id === edge.from) || !batches.some((b) => b.id === edge.to))
        continue;
      const parent = this.batchCode(edge.from),
        child = this.batchCode(edge.to);
      this.divisiones.update((list) => {
        const existing = list.find((d) => d.loteOrigenId === parent);
        return existing
          ? list.map((d) =>
              d === existing
                ? { ...d, lotesHijosIds: [...new Set([...d.lotesHijosIds, child])] }
                : d,
            )
          : [
              ...list,
              {
                id: 'division-' + edge.from,
                loteOrigenId: parent,
                lotesHijosIds: [child],
                operarioId: '',
                empresaId: this.lote(parent)!.empresaId,
                fecha: this.lote(child)!.fechaRegistro,
              },
            ];
      });
    }
    this.requestedLineages.add(code);
    return batches.map(batch => batch.traceabilityId);
  }
  /** Sin sesión no consulta nada; si falla, conserva el último valor (el contador es informativo). */
  async refreshUnreadCount(): Promise<void> {
    const token = this.session.token();
    if (!token) return;
    try {
      const { count } = await this.api.request<{ count: number }>('GET', '/notifications/unread-count');
      if (token === this.session.token() && Number.isFinite(count)) this.unreadCount.set(Math.max(0, Math.trunc(count)));
    } catch { /* se reintenta en el próximo sondeo */ }
  }
  /** Cambia el filtro o la página de la bandeja y la pide al servidor. */
  async consultarNotificaciones(query: Partial<NotificationQuery>): Promise<void> {
    this.notificationQuery = { ...this.notificationQuery, ...query };
    await this.refreshNotifications().catch(() => {});
  }
  /** `GET /notifications` con la última consulta (`unread`, `type`, `page`, `size`): el servidor filtra y pagina. */
  async refreshNotifications() {
    const token = this.session.token();
    const query = this.notificationQuery;
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
    if (query.unread) params.set('unread', 'true');
    if (query.type) params.set('type', query.type);
    let result: ApiPage<A.ApiNotification>;
    try {
      result = await this.api.request<ApiPage<A.ApiNotification>>('GET', '/notifications?' + params.toString());
      this.notificationError.set('');
    } catch (error) {
      this.notificationError.set(errorMessage(error));
      throw error;
    }
    if (token !== this.session.token() || query !== this.notificationQuery) return;
    this.notificacionesTotal.set(result.totalElements);
    if (!query.unread && !query.type) this.notificacionesTodas.set(result.totalElements);
    this.notificaciones.set(result.items.map(n => ({ id: n.id, tipo: tipoDeNotificacion(n.type), titulo: n.title, descripcion: n.message,
      fecha: n.createdAt, leida: n.read, para: this.session.rol(), empresaId: n.companyId ?? undefined, datos: n.data ?? undefined,
      entidad: n.resourceType && n.resourceId ? { tipo: n.resourceType, id: n.resourceType === 'lote' ? this.batchCode(n.resourceId) : n.resourceId } : undefined,
    })));
  }
  async marcarLeida(id: string, read = true) {
    try {
      await this.api.request('PUT', '/notifications/' + id + '/read?value=' + read);
      await Promise.all([this.refreshNotifications(), this.refreshUnreadCount()]);
      return true;
    } catch (error) {
      if (errorCode(error) === A.API_ERROR.NOTIFICATION_NOT_FOUND) {
        // ya no existe (la borró otra sesión o no es tuya): no es un fallo del usuario; se actualiza la bandeja y se avisa sin alarma
        await Promise.all([this.refreshNotifications().catch(() => {}), this.refreshUnreadCount()]);
        this.ui.toast('Esa notificación ya no está disponible. Se actualizó la bandeja.');
        return false;
      }
      this.ui.toast(errorMessage(error), true);
      return false;
    }
  }
  async marcarTodasLeidas() {
    try {
      await this.api.request('PUT', '/notifications/read-all');
      await Promise.all([this.refreshNotifications(), this.refreshUnreadCount()]);
    } catch (error) { this.ui.toast(errorMessage(error), true); }
  }
  private async refreshQuality() {
    const token = this.session.token();
    const [controls, failures, batches] = await Promise.all([
      this.api.all<A.ApiQualityControl>('/quality/controls'),
      this.api.all<A.ApiQualityFailure>('/quality/failures'),
      this.api.all<{ batchId: string; qualityStatus: string; qualityHold: boolean }>('/quality/batches'),
    ]);
    if (token !== this.session.token()) return;
    this.lotes.update(lots => lots.map(lot => {
      const state = batches.find(b => this.batchCode(b.batchId) === lot.id);
      return state ? { ...lot, calidad: this.reverse(C.CALIDAD_API, state.qualityStatus), qualityHold: state.qualityHold } : lot;
    }));
    this.controles.set(controls.map(c => ({ id: c.id, loteId: this.batchCode(c.batchId), empresaId: c.companyId,
      administradorId: c.inspectorId, tipo: c.controlType === 'EVALUATION' ? 'Evaluación' : 'Reevaluación',
      inicio: c.startDate, fin: c.endDate ?? undefined, status: c.status, finishPending: c.finishPending, cancelReason: c.cancelReason,
      pruebas: c.tests.map(p => ({ id: p.id, criterio: p.criterionName, valorEsperado: p.expectedValue, valorObtenido: p.obtainedValue,
        unidad: p.unit, resultado: p.testResult === 'CONFORMING' ? 'Conforme' : 'Con falla', observaciones: p.remarks ?? undefined })),
      evidencias: this.control(c.id)?.evidencias ?? [],
    })));
    this.fallas.set(failures.map(f => ({ id: f.id, loteId: this.batchCode(f.batchId), empresaId: f.companyId, controlId: f.controlId,
      causa: f.rootCause ?? undefined, cantidadAfectadaKg: f.affectedQtyKg ?? undefined, observaciones: f.observations ?? undefined,
      estado: f.status === 'CONFIRMED' ? 'Confirmada' : f.status === 'RESOLVED' ? 'Resuelta' : undefined,
      discardPending: f.discardPending, fecha: this.control(f.controlId)?.fin ?? this.control(f.controlId)?.inicio ?? '',
      evidencias: this.falla(f.id)?.evidencias ?? [],
    })));
  }
  rolLabel(role: Rol) {
    return role === 'Administrador de Empresa' ? 'Administrador' : role;
  }
  tipoControlPara = (l: Lote) => (this.controlesDe(l.id).some(c => c.status === 'FINISHED') ? 'Reevaluación' : 'Evaluación');
  puedeIniciarControl(l: Lote): { ok: boolean; motivo: string } {
    if (this.controlActivo(l.id))
      return { ok: false, motivo: 'Ya existe un control activo para este lote.' };
    if (l.fase !== 'Disponible')
      return { ok: false, motivo: `Fase ${l.fase}: solo lotes Disponibles reciben control.` };
    if (!['No revisado', 'Por reevaluar', 'Posible falla derivada'].includes(l.calidad))
      return {
        ok: false,
        motivo:
          l.calidad === 'Con falla'
            ? 'Primero decide la falla (reevaluar o descartar).'
            : 'El lote ya fue evaluado como Conforme.',
      };
    return { ok: true, motivo: '' };
  }

  async iniciarControl(loteId: string): Promise<Resultado> {
    const batchId = this.batchId(loteId);
    try {
      for (let attempt = 0; attempt < 5; attempt++) {
        const batches = await this.api.all<{ batchId: string }>('/quality/batches');
        if (batches.some(batch => batch.batchId === batchId)) break;
        if (attempt === 4) return { ok: false, error: 'El lote se está sincronizando con Calidad. Espera unos segundos y vuelve a iniciar el control.' };
        await new Promise(resolve => setTimeout(resolve, 500));
      }
    } catch (error) { return { ok: false, error: errorMessage(error) }; }
    return this.mutate<A.ApiQualityControl>('POST', '/quality/batches/' + this.batchId(loteId) + '/controls', {}, c => c.id);
  }

  agregarPrueba(controlId: string, p: Omit<Prueba, 'id'>) {
    return this.mutate('POST', '/quality/controls/' + controlId + '/tests', { criterionName: p.criterio,
      expectedValue: p.valorEsperado, obtainedValue: p.valorObtenido, unit: p.unidad,
      testResult: p.resultado === 'Conforme' ? 'CONFORMING' : 'FAILED', remarks: p.observaciones });
  }

  resultadoControl = (c: ControlCalidad) =>
    !c.pruebas.length
      ? undefined
      : c.pruebas.some((p) => p.resultado === 'Con falla')
        ? 'Con falla'
        : 'Conforme';

  finalizarControl(controlId: string): Promise<Resultado> {
    return this.mutate<{ failure: A.ApiQualityFailure | null }>('POST', '/quality/controls/' + controlId + '/finish', undefined, r => r.failure?.id ?? '');
  }

  actualizarFalla(
    id: string,
    patch: Pick<Falla, 'causa' | 'cantidadAfectadaKg' | 'observaciones'>,
  ) {
    return this.mutate('PUT', '/quality/failures/' + id, { rootCause: patch.causa, affectedQtyKg: patch.cantidadAfectadaKg, observations: patch.observaciones });
  }

  enviarAReevaluacion(fallaId: string) {
    return this.mutate('POST', '/quality/failures/' + fallaId + '/reevaluation');
  }

  descartarLote(fallaId: string): Promise<Resultado> {
    return this.mutate('POST', '/quality/failures/' + fallaId + '/discard');
  }
  descargarReporte(controlId: string) {
    return this.api.download('/quality/controls/' + controlId + '/report', 'control-' + controlId + '.pdf');
  }

  relacionadosDerivados(loteId: string): string[] {
    return this.familiaFalla(loteId).filter((id) => {
      const l = this.lote(id);
      return (
        l &&
        l.fase === 'Disponible' &&
        !['Con falla', 'Por reevaluar', 'Posible falla derivada'].includes(l.calidad)
      );
    });
  }

  familiaFalla(loteId: string): string[] {
    const familia = new Set<string>(this.descendientes(loteId));

    const rama = (id: string) => {
      familia.add(id);
      this.divisionDe(id)?.lotesHijosIds.forEach(rama);
    };

    this.procesoOrigen(loteId)?.salidasIds.forEach(rama);

    for (const a of [loteId, ...this.ancestros(loteId)]) {
      this.divisionDe(a)?.lotesHijosIds.forEach(rama);
    }
    familia.delete(loteId);
    this.ancestros(loteId).forEach((a) => familia.delete(a));
    return [...familia].sort();
  }

  estadoFalla(f: Falla): string {
    if (f.estado) return f.estado;
    return this.lote(f.loteId)?.calidad === 'Por reevaluar'
      ? 'En reevaluación'
      : 'Pendiente de decisión';
  }
}

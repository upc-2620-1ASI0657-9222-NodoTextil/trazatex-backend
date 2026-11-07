                                                                                              

export type Rol = 'Operario' | 'Administrador de Empresa' | 'Dueño de Licencia';

export type FaseOperativa =
  | 'Disponible'
  | 'En transformación'
  | 'En transferencia'
  | 'En control de calidad'
  | 'Procesado'
  | 'Dividido'
  | 'Descartado';

export type EstadoCalidad =
  | 'No revisado'
  | 'Conforme'
  | 'Con falla'
  | 'Por reevaluar'
  | 'Posible falla derivada';

export const FASES: FaseOperativa[] = [
  'Disponible',
  'En transformación',
  'En transferencia',
  'En control de calidad',
  'Procesado',
  'Dividido',
  'Descartado',
];

export const ESTADOS_CALIDAD: EstadoCalidad[] = [
  'No revisado',
  'Conforme',
  'Con falla',
  'Por reevaluar',
  'Posible falla derivada',
];

export const MATERIALES = [
  'Fibra de algodón Pima',
  'Fibra de alpaca',
  'Hilo peinado',
  'Hilo mezcla',
  'Tela jersey',
  'Tela piqué',
  'Prenda final',
] as const;
export type TipoMaterial = (typeof MATERIALES)[number];

export interface Evidencia {
  status?: string;
  id: string;
  nombre: string;
  url: string;
  fecha: string;       
}

export interface Componente {
  nombre: string;
  porcentaje: number;
}

export interface DatosComerciales {
  distribuidor: string;
  precioVenta: number | null;
  moneda: 'PEN' | 'USD';
  fechaSalida: string;
  referencia: string;
}

export interface Lote {
  failureCause?: string;
  qualityTests?: { criterionName: string; expectedValue: string; obtainedValue: string; unit: string; testResult: string }[];
  qualityHold?: boolean;
  id: string;                              
  nombre?: string;
  qr: string;                         
  proveedor?: string;
  origen: string;
  material: TipoMaterial;
  composicion: Componente[];
  cantidadKg: number;
  caracteristicasRecepcion: string;
  empresaId: string;
  fase: FaseOperativa;
  calidad: EstadoCalidad;
  fechaRegistro: string;
  productoFinal: boolean;
  comercial?: DatosComerciales;
  evidencias: Evidencia[];
}

export interface Division {
  id: string;
  loteOrigenId: string;
  lotesHijosIds: string[];
  operarioId: string;
  empresaId: string;
  fecha: string;
}

export interface EntradaProceso {
  loteId: string;
  cantidadKg: number;
}

export interface Proceso {
  id: string;
  operarioId: string;
  empresaId: string;
  entradas: EntradaProceso[];
  tipo: string;                          
  maquinariaId: string;
  sharedMachineName?: string;
  inicio: string;
  fin?: string;
  salidasIds: string[];
  mermaKg?: number;
  desperdicioKg?: number;
  motivoDesperdicio?: string;
  evidencias: Evidencia[];
}

export const TIPOS_TRANSFORMACION = ['Hilado', 'Tejido', 'Teñido'] as const;

export type EstadoMaquinaria = 'Activa' | 'Fuera de servicio' | 'Inactiva';

export interface Maquinaria {
  codigo: string;
  nombre: string;
  tipo: string;
  estado: EstadoMaquinaria;
  empresaId: string;
}

export type EstadoTransferencia = 'Pendiente' | 'Aceptada' | 'Rechazada';

export interface Transferencia {
  id: string;
  origenId: string;
  destinoId: string;
  lotesIds: string[];
  operarioId: string;
  inicio: string;
  recepcion?: string;
  rechazo?: string;
  motivoRechazo?: string;
  estado: EstadoTransferencia;
}

export type TipoControl = 'Evaluación' | 'Reevaluación';
export type ResultadoPrueba = 'Conforme' | 'Con falla';

export interface Prueba {
  id: string;
  criterio: string;
  valorEsperado: string;
  valorObtenido: string;
  unidad: string;
  resultado: ResultadoPrueba;
  observaciones?: string;
}

export interface ControlCalidad {
  status?: 'STARTING' | 'IN_PROGRESS' | 'FINISHED' | 'CANCELLED';
  finishPending?: boolean;
  cancelReason?: string | null;
  id: string;
  loteId: string;
  empresaId: string;
  administradorId: string;
  tipo: TipoControl;
  inicio: string;
  fin?: string;
  pruebas: Prueba[];
  evidencias: Evidencia[];
}

                                                                                             
export type ResolucionFalla = 'Confirmada' | 'Resuelta';

export interface Falla {
  discardPending?: boolean;
  id: string;
  loteId: string;
  controlId: string;
  empresaId: string;
  causa?: string;
  cantidadAfectadaKg?: number;
  observaciones?: string;
  estado?: ResolucionFalla;
  fecha: string;
  evidencias: Evidencia[];
}

export type EstadoEmpresa = 'Activa' | 'Inactiva';

export interface Empresa {
  id: string;
  razonSocial: string;
  ruc: string;
  actividades: string[];
  estado: EstadoEmpresa;
  correoPrimerAdmin: string;
}

export interface Licencia {
  tipo: 'Perpetua';
  limiteEmpresas: number;
  limiteUsuariosPorEmpresa: number;
}

export type EstadoAcceso = 'Activo' | 'Inactivo';

export interface Usuario {
  id: string;
  nombres: string;
  apellidos: string;
  correo: string;
  cargo: string;
  rol: Rol;
  empresaId?: string;
  estado: EstadoAcceso;
}

export type EstadoInvitacion = 'Pendiente' | 'Aceptada' | 'Expirada' | 'Cancelada';

export interface Invitacion {
  id: string;
  nombre: string;
  correo: string;
  empresaId: string;
  rol: Rol;
  estado: EstadoInvitacion;
  enviada: string;
  expira: string;
}

export type EstadoSolicitud = 'Pendiente' | 'Aprobada' | 'Rechazada';

export interface SolicitudAdmin {
  id: string;
  nombre: string;
  correo: string;
  cargo: string;
  empresaId: string;
  estado: EstadoSolicitud;
  fecha: string;
}

export const CAMPOS_COMPARTIDOS = [
  'Traceability ID',
  'Empresa responsable',
  'Relaciones de genealogía',
  'Tipo de material',
  'Fase operativa',
  'Estado de calidad',
  'Fecha de registro',
  'Indicador de producto final',
] as const;

export const CAMPOS_CONFIGURABLES = [
  'proveedor',
  'origen',
  'cantidad',
  'composicion',
  'caracteristicasRecepcion',
  'maquinaria',
  'merma',
  'desperdicio',
  'motivoDesperdicio',
  'causaFalla',
  'valoresControl',
] as const;
export type CampoConfigurable = (typeof CAMPOS_CONFIGURABLES)[number];

export const ETIQUETA_CAMPO: Record<CampoConfigurable, string> = {
  proveedor: 'Proveedor',
  origen: 'Origen geográfico',
  cantidad: 'Cantidad',
  composicion: 'Composición',
  caracteristicasRecepcion: 'Características de recepción',
  maquinaria: 'Maquinaria',
  merma: 'Merma',
  desperdicio: 'Desperdicio',
  motivoDesperdicio: 'Motivo del desperdicio',
  causaFalla: 'Causa de falla',
  valoresControl: 'Valores detallados de controles de calidad',
};

export const CAMPOS_PRIVADOS = [
  'Identidad del operario responsable',
  'Observaciones internas de controles',
  'Observaciones internas de fallas',
  'Datos comerciales de producto final',
] as const;

export type Visibilidad = 'Privado' | 'Compartido';
export type ConfigPrivacidad = Record<string, Partial<Record<CampoConfigurable, Visibilidad>>>;

export type TipoNotificacion =
  | 'Invitación'
  | 'Transferencia entrante'
  | 'Transferencia aceptada'
  | 'Transferencia rechazada'
  | 'Alerta de calidad'
  | 'Posible falla derivada'
  | 'Solicitud de administrador'
  | 'Solicitud revisada'
  | 'Capacidad'
  | 'Notificación';

/** `type` de `GET /notifications`: un código estable del servicio; el texto que se muestra lo decide el front. */
export type CodigoNotificacion =
  | 'ADMIN_REQUEST_SUBMITTED'
  | 'ADMIN_REQUEST_RESOLVED'
  | 'TRANSFER_RECEIVED'
  | 'TRANSFER_ACCEPTED'
  | 'TRANSFER_REJECTED'
  | 'POTENTIAL_DERIVED_FAILURE';

export const TIPO_DE_NOTIFICACION: Readonly<Record<CodigoNotificacion, TipoNotificacion>> = {
  ADMIN_REQUEST_SUBMITTED: 'Solicitud de administrador',
  ADMIN_REQUEST_RESOLVED: 'Solicitud revisada',
  TRANSFER_RECEIVED: 'Transferencia entrante',
  TRANSFER_ACCEPTED: 'Transferencia aceptada',
  TRANSFER_REJECTED: 'Transferencia rechazada',
  POTENTIAL_DERIVED_FAILURE: 'Posible falla derivada',
};

/** Un código que este front no conoce (un tipo nuevo del servicio) se muestra como «Notificación», nunca en blanco. */
export function tipoDeNotificacion(codigo: string): TipoNotificacion {
  return TIPO_DE_NOTIFICACION[codigo as CodigoNotificacion] ?? 'Notificación';
}

export interface EntidadRef {
  tipo: 'lote' | 'proceso' | 'control' | 'falla' | 'transferencia' | 'empresa' | 'usuario' | 'solicitud';
  id: string;
}

export interface Notificacion {
  id: string;
  tipo: TipoNotificacion;
  titulo: string;
  descripcion: string;
  entidad?: EntidadRef;
  fecha: string;
  leida: boolean;
  para: Rol;
  empresaId?: string;
  /** Ids que acompañan a la notificación (`data` del servicio): transferencia, solicitud, falla, lotes. */
  datos?: Record<string, unknown>;
}



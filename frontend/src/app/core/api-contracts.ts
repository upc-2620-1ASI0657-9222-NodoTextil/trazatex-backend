export const ROLES_API = {
  Operario: 'OPERATOR',
  'Administrador de Empresa': 'COMPANY_ADMIN',
  'Dueño de Licencia': 'LICENSE_OWNER',
} as const;

export const FASES_API = {
  Disponible: 'AVAILABLE',
  'En transformación': 'IN_TRANSFORMATION',
  'En transferencia': 'IN_TRANSFER',
  'En control de calidad': 'IN_QUALITY_CONTROL',
  Procesado: 'PROCESSED',
  Dividido: 'SPLIT',
  Descartado: 'DISCARDED',
} as const;

export const CALIDAD_API = {
  'No revisado': 'NOT_REVIEWED',
  Conforme: 'CONFORMING',
  'Con falla': 'FAILED',
  'Por reevaluar': 'PENDING_REEVALUATION',
  'Posible falla derivada': 'POTENTIAL_DERIVED_FAILURE',
} as const;

export const TRANSFORMACIONES_API = {
  Hilado: 'SPINNING',
  Tejido: 'WEAVING',
  Teñido: 'DYEING',
} as const;

export const ACTIVIDADES_API = {
  Hilandería: 'SPINNING',
  Tejeduría: 'WEAVING',
  Tintorería: 'DYEING',
  Acabados: 'FINISHING',
  Corte: 'CUTTING',
  Confección: 'SEWING',
  Marca: 'BRAND',
} as const;

export const CAMPOS_PRIVACIDAD_API = {
  proveedor: 'SUPPLIER',
  origen: 'ORIGIN',
  cantidad: 'QUANTITY',
  composicion: 'COMPOSITION',
  caracteristicasRecepcion: 'RECEPTION_CHARACTERISTICS',
  maquinaria: 'MACHINERY',
  merma: 'SHRINKAGE',
  desperdicio: 'WASTE',
  motivoDesperdicio: 'WASTE_REASON',
  causaFalla: 'FAILURE_CAUSE',
  valoresControl: 'QUALITY_TEST_VALUES',
} as const;

export const ESTADOS_MAQUINARIA_API = {
  Activa: 'ACTIVE',
  'Fuera de servicio': 'OUT_OF_SERVICE',
  Inactiva: 'INACTIVE',
} as const;

export const ESTADOS_TRANSFERENCIA_API = {
  Pendiente: 'PENDING',
  Aceptada: 'ACCEPTED',
  Rechazada: 'REJECTED',
} as const;

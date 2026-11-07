import { ApiRole } from './api.service';
export interface ApiCompany {
  id: string;
  ruc: string;
  businessName: string;
  activities: string[];
  status: 'ACTIVE' | 'INACTIVE';
}
export interface ApiInvitation {
  id: string;
  email: string;
  displayName: string;
  firstName: string;
  lastName: string;
  position: string;
  role: ApiRole;
  companyId: string;
  status: 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED';
  createdAt: string;
  expiresAt: string;
}
export interface ApiAdminRequest {
  id: string;
  companyId: string;
  fullName: string;
  email: string;
  position: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
}
export interface ApiLicense {
  maxCompanies: number;
  maxUsersPerCompany: number;
  companiesUsed: number;
  companiesAvailable: number;
  companies: {
    companyId: string;
    businessName: string;
    usedSlots: number;
    maxUsers: number;
    availableSlots: number;
  }[];
}
export interface ApiPrivacy {
  companyId: string;
  fields: Record<string, 'SHARED' | 'PRIVATE'>;
}
export interface ApiMachine {
  id: string;
  companyId: string;
  internalCode: string;
  name: string;
  type: string;
  status: 'ACTIVE' | 'OUT_OF_SERVICE' | 'INACTIVE';
}
export interface ApiCommercial {
  buyerOrDistributor: string;
  salePrice: number | null;
  currency: 'PEN' | 'USD';
  commercialExitDate: string;
  commercialReference: string;
}
export interface ApiBatch {
  id: string;
  traceabilityId: string;
  qrCode: string;
  companyId: string;
  name?: string;
  materialType: string;
  quantityKg?: number;
  composition?: { name: string; percentage: number }[];
  phase: string;
  qualityStatus: string;
  qualityHold?: boolean;
  finalProduct: boolean;
  origin?: string;
  supplierName?: string;
  receptionCharacteristics?: string;
  createdAt: string;
  commercialData?: ApiCommercial;
}
/** `GET /batches/qr/{qrCode}`: propio → todos los datos; ajeno → solo los siempre compartidos (sin `name`). Si no existe, 404 `QR_NOT_FOUND`. */
export interface ApiQrLookup {
  access: 'OWN' | 'SHARED';
  batch: ApiBatch;
}
/** Un resultado de `GET /traceability/lineages` (US30): la vista visible para la empresa que consulta. */
export interface ApiLineageHit {
  batchId: string;
  traceabilityId: string;
  qrCode: string;
  companyId: string;
  businessName: string;
  materialType: string;
  phase: string;
  qualityStatus: string;
  createdAt: string;
  finalProduct: boolean;
  access: 'OWN' | 'SHARED';
}
export interface ApiTransformation {
  id: string;
  companyId: string;
  type: string;
  machineId: string;
  operatorId: string;
  inputs: { batchId: string; quantityKg: number }[];
  outputBatchIds: string[];
  wasteKg?: number;
  wasteReason?: string;
  shrinkageKg?: number;
  startTime: string;
  endTime?: string;
}
export interface ApiTransfer {
  id: string;
  originCompanyId: string;
  targetCompanyId: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  batchIds: string[];
  departureTime: string;
  receptionTime?: string;
  resolvedAt?: string;
  rejectionReason?: string;
  initiatedBy: string;
}
export interface ApiEvidence {
  id: string;
  status: string;
  url: string | null;
  originalFilename: string;
  createdAt: string;
}
export interface VisibleBatch extends ApiBatch {
  failureCause?: string;
  qualityTests?: { criterionName: string; expectedValue: string; obtainedValue: string; unit: string; testResult: string }[];
  kind: 'BATCH';
  access: 'OWN' | 'SHARED';
  businessName: string;
}
export interface VisibleTransformation {
  id: string;
  kind: 'TRANSFORMATION';
  companyId: string;
  businessName: string;
  type: string;
  startedAt: string;
  completedAt?: string;
  machineName?: string;
  machineType?: string;
  wasteKg?: number;
  wasteReason?: string;
  shrinkageKg?: number;
}
export interface ApiLineage {
  complete: boolean;
  truncated: boolean;
  nodes: (VisibleBatch | VisibleTransformation)[];
  edges: { type: string; from: string; to: string }[];
}
export type ApiBatchSummary = Pick<
  ApiBatch,
  | 'id'
  | 'traceabilityId'
  | 'qrCode'
  | 'name'
  | 'materialType'
  | 'quantityKg'
  | 'phase'
  | 'qualityStatus'
  | 'qualityHold'
  | 'finalProduct'
  | 'createdAt'
  | 'supplierName'
  | 'origin'
>;

export interface ApiQualityTest {
  id: string; criterionName: string; expectedValue: string; obtainedValue: string;
  unit: string; testResult: 'CONFORMING' | 'FAILED'; remarks: string | null;
}
export interface ApiQualityControl {
  id: string; batchId: string; companyId: string; controlType: 'EVALUATION' | 'REEVALUATION';
  status: 'STARTING' | 'IN_PROGRESS' | 'FINISHED' | 'CANCELLED'; inspectorId: string;
  startDate: string; endDate: string | null; finishPending: boolean; cancelReason: string | null;
  tests: ApiQualityTest[];
}
export interface ApiQualityFailure {
  id: string; batchId: string; companyId: string; controlId: string; rootCause: string | null;
  affectedQtyKg: number | null; observations: string | null; status: 'OPEN' | 'CONFIRMED' | 'RESOLVED'; discardPending: boolean;
}
/** Códigos de error del contrato (`ApiError.code`) que el front ramifica. */
export const API_ERROR = {
  /** `PATCH|PUT /notifications/{id}/read` de una notificación que no existe o es de otro usuario o empresa (mismo cuerpo en ambos casos). */
  NOTIFICATION_NOT_FOUND: 'NOTIFICATION_NOT_FOUND',
  /** 401 del Gateway (`AuthenticationFailedException.Reason`): el `code` dice por qué terminó la sesión. */
  MISSING_TOKEN: 'MISSING_TOKEN',
  INVALID_TOKEN: 'INVALID_TOKEN',
  TOKEN_EXPIRED: 'TOKEN_EXPIRED',
  USER_INACTIVE: 'USER_INACTIVE',
} as const;

/**
 * Motivo con el que Quality cancela un control cuyo inicio rechazó Production: es el `code` del error de Production (o
 * `MANUAL_CANCELLATION`). Se muestra con estas frases y nunca en crudo; un código nuevo cae en la frase genérica.
 */
export const CANCEL_REASON_TEXT: Readonly<Record<string, string>> = {
  INVALID_PHASE_TRANSITION: 'el lote ya no estaba disponible para un control (cambió de fase mientras tanto)',
  BATCH_ON_QUALITY_HOLD: 'el lote está retenido por una falla de calidad',
  BATCH_IS_FINAL_PRODUCT: 'el lote es un producto final',
  BATCH_NOT_FOUND: 'Producción no encontró el lote',
  BATCH_COMPANY_MISMATCH: 'el lote pertenece a otra empresa',
  MANUAL_CANCELLATION: 'se canceló manualmente',
};
export function cancelReasonText(code: string | null | undefined): string {
  return (code && CANCEL_REASON_TEXT[code]) || 'Producción no aceptó el inicio';
}
export interface ApiNotification {
  /** Código estable (`CodigoNotificacion`); puede traer uno que este front aún no conoce. */
  id: string; type: string; title: string; message: string;
  resourceType: import('./models').EntidadRef['tipo'] | null; resourceId: string | null;
  data: Record<string, unknown> | null;
  createdAt: string; read: boolean; companyId: string | null; role: string;
}

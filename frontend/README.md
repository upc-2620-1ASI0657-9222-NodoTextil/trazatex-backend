# FRONTEND
Aplicación web de TrazaTex para las empresas textiles.

Tecnologías: Angular 21 y TypeScript.
Los controles, fallas, reportes, notificaciones e indicadores utilizan el API Gateway y aplican los permisos de cada rol. Las pantallas mantienen el diseño existente. Los detalles de transferencias y las genealogías se consultan al abrir el recurso para evitar recargar todo el historial en cada operación.

Compilación: `npm ci` y `npm run build`. Las verificaciones de navegador están en `e2e`; requieren los servicios y las cuentas privadas indicadas por cada prueba. Consulta [el despliegue local](../INSTRUCCIONES_DESPLIEGUE_LOCAL.md).

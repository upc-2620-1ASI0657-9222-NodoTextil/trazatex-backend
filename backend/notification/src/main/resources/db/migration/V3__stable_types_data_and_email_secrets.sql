-- I5: `type` pasa a ser un código estable (antes era texto de interfaz) y cada notificación lleva `data` con los ids que el front necesita.
ALTER TABLE notification ADD COLUMN data JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE notification SET type = CASE
    WHEN title = 'Nueva solicitud de administrador' THEN 'ADMIN_REQUEST_SUBMITTED'
    WHEN title = 'Solicitud revisada' THEN 'ADMIN_REQUEST_RESOLVED'
    WHEN type = 'Transferencia entrante' THEN 'TRANSFER_RECEIVED'
    WHEN type = 'Transferencia aceptada' THEN 'TRANSFER_ACCEPTED'
    WHEN type = 'Transferencia rechazada' THEN 'TRANSFER_REJECTED'
    WHEN type = 'Posible falla derivada' THEN 'POTENTIAL_DERIVED_FAILURE'
    ELSE type END;

UPDATE notification SET data = jsonb_build_object('requestId', resource_id) WHERE resource_type = 'solicitud' AND resource_id IS NOT NULL;
UPDATE notification SET data = jsonb_build_object('transferId', resource_id) WHERE resource_type = 'transferencia' AND resource_id IS NOT NULL;
UPDATE notification SET data = jsonb_build_object('batchIds', jsonb_build_array(resource_id)) WHERE resource_type = 'lote' AND resource_id IS NOT NULL;

-- I2: el cuerpo de un correo (en una invitación lleva el token de un solo uso) solo existe mientras el correo está pendiente.
UPDATE deferred_email SET status = 'UNDELIVERABLE' WHERE status NOT IN ('PENDING', 'SENT', 'FAILED', 'EXPIRED', 'UNDELIVERABLE');
UPDATE deferred_email SET body = '' WHERE status <> 'PENDING';
ALTER TABLE deferred_email ADD CONSTRAINT deferred_email_status_valid
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'EXPIRED', 'UNDELIVERABLE'));
ALTER TABLE deferred_email ADD CONSTRAINT deferred_email_body_only_while_pending
    CHECK (status = 'PENDING' OR body = '');

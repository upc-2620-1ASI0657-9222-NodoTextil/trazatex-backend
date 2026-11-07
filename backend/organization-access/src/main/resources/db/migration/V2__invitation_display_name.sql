-- Nombre para mostrar de la invitación: el que trae quien invita (fullName de la solicitud de administrador,
-- TRAZATEX_OWNER_NAME), sin dividirlo en nombres y apellidos. Va en InvitationCreated.fullName.
ALTER TABLE invitations ADD COLUMN display_name VARCHAR(200);
UPDATE invitations SET display_name = first_name || ' ' || last_name WHERE first_name IS NOT NULL AND last_name IS NOT NULL;

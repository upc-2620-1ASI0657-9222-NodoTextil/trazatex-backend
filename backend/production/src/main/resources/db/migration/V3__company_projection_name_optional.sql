-- Una empresa puede conocerse primero por un CompanyStatusChanged (su CompanyRegistered no llegó: venció por retención, fue a la
-- DLT...). Bloquear a una empresa inactiva es más seguro que ignorarla, así que la fila se crea igual y el nombre se completa
-- cuando llegue su CompanyRegistered.
ALTER TABLE company_projection ALTER COLUMN business_name DROP NOT NULL;

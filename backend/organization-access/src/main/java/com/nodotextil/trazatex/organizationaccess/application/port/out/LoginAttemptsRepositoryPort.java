package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.LoginAttempts;

import java.time.Duration;
import java.time.Instant;

/** Intentos fallidos por correo normalizado (RNF-06), exista o no la cuenta. */
public interface LoginAttemptsRepositoryPort {

    /**
     * Devuelve el registro del correo con su fila bloqueada hasta el fin de la transacción, creándolo vacío si no existía
     * (el alta no pisa a otra transacción: {@code INSERT ... ON CONFLICT DO NOTHING}). Los intentos simultáneos del mismo
     * correo se cuentan uno por uno.
     */
    LoginAttempts lockOrCreate(Email email, Instant now);

    void save(LoginAttempts attempts);

    /** Un login correcto olvida los fallos. */
    void delete(Email email);

    /**
     * Borra lo vencido: bloqueos que ya terminaron y contadores parciales sin actividad en {@code idleRetention}.
     * @return filas borradas
     */
    int deleteExpired(Instant now, Duration idleRetention);
}

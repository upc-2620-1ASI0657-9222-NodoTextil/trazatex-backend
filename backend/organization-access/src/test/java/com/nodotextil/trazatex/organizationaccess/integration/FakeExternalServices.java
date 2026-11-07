package com.nodotextil.trazatex.organizationaccess.integration;

import com.nodotextil.trazatex.organizationaccess.application.exception.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordBreachCheckerPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidatorPort;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dobles de Chequea.pe y HIBP para las pruebas de integración (los adaptadores reales se prueban en 02d con WireMock). */
final class FakeExternalServices {

    private FakeExternalServices() {
    }

    static class FakeRucValidator implements RucValidatorPort {

        final Set<String> rejected = ConcurrentHashMap.newKeySet();
        final AtomicBoolean unavailable = new AtomicBoolean();
        /** Si no es nulo, la prueba usa el adaptador real (contra WireMock) en lugar del doble. */
        volatile RucValidatorPort delegate;

        @Override
        public void ensureActiveAndHabido(Ruc ruc) {
            if (delegate != null) {
                delegate.ensureActiveAndHabido(ruc);
                return;
            }
            if (unavailable.get()) {
                throw new ExternalServiceUnavailableException(ErrorCodes.RUC_VALIDATION_UNAVAILABLE, "Reintente más tarde.");
            }
            if (rejected.contains(ruc.value())) {
                throw new ValidationException(ErrorCodes.RUC_INVALID, "El RUC no es válido.", List.of());
            }
        }

        void reset() {
            rejected.clear();
            unavailable.set(false);
            delegate = null;
        }
    }

    static class FakePasswordBreachChecker implements PasswordBreachCheckerPort {

        final Set<String> compromised = ConcurrentHashMap.newKeySet();
        final AtomicBoolean unavailable = new AtomicBoolean();
        volatile PasswordBreachCheckerPort delegate;

        @Override
        public Result check(String rawPassword) {
            if (delegate != null) {
                return delegate.check(rawPassword);
            }
            if (unavailable.get()) {
                return Result.UNAVAILABLE;
            }
            return compromised.contains(rawPassword) ? Result.COMPROMISED : Result.CLEAN;
        }

        void reset() {
            compromised.clear();
            unavailable.set(false);
            delegate = null;
        }
    }
}

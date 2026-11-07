package com.nodotextil.trazatex.production.strictness;

import io.cucumber.java.PendingException;
import io.cucumber.java.es.Dado;

/** Pasos de los .feature de {@code strictness/}: solo sirven para comprobar que el modo estricto de verdad hace fallar. No son de los escenarios. */
public class StrictnessSteps {

    @Dado("^que existe un paso definido$")
    public void defined() {
    }

    @Dado("^que existe un paso pendiente$")
    public void pending() {
        throw new PendingException("pendiente a propósito");
    }

    @Dado("^que existe un paso ambiguo$")
    public void ambiguousOne() {
    }

    @Dado("^que existe un paso (ambiguo)$")
    public void ambiguousTwo(String word) {
    }
}

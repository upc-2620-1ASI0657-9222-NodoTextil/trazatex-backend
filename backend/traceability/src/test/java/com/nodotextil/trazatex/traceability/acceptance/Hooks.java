package com.nodotextil.trazatex.traceability.acceptance;

import io.cucumber.java.Before;

public class Hooks {

    private final Platform platform;

    public Hooks(Platform platform) {
        this.platform = platform;
    }

    @Before(order = 0)
    public void emptyGraph() {
        platform.emptyGraph();
    }
}

package com.nodotextil.trazatex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrazatexBackendApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(TrazatexBackendApplication.class).isNotNull();
    }
}

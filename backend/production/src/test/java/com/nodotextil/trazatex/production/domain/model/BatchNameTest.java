package com.nodotextil.trazatex.production.domain.model;

import org.junit.jupiter.api.Test;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import static com.nodotextil.trazatex.production.domain.Fixtures.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class BatchNameTest {
    @Test void acceptsTwentyAndRejectsTwentyOneCharactersOnEveryCreationPath() {
        var factory = factory(73);
        String valid = "12345678901234567890";
        var data = new BatchFactory.Registration(valid, "Fibra", composition("Algodon", "100"), Quantity.ofKg("10"), "Piura", "Cooperativa", "Fibra limpia");
        var batch = factory.register(data, COMPANY, OPERATOR, NOW);
        assertThat(batch.name()).isEqualTo(valid);
        var invalid = new BatchFactory.Registration(valid + "1", "Fibra", data.composition(), data.quantity(), data.origin(), data.supplierName(), data.receptionCharacteristics());
        assertThatThrownBy(() -> factory.register(invalid, COMPANY, OPERATOR, NOW)).isInstanceOf(ValidationException.class).hasMessageContaining("20");
        assertThatThrownBy(() -> factory.split(batch, List.of(new BatchFactory.ChildSpec(valid + "1", Quantity.ofKg("5")), new BatchFactory.ChildSpec("Reserva", Quantity.ofKg("5"))), OPERATOR, NOW)).isInstanceOf(ValidationException.class);
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThatThrownBy(() -> factory.output(new BatchFactory.OutputSpec(valid + "1", "Hilo", Quantity.ofKg("9"), false, CommercialData.EMPTY), data.composition(), COMPANY, OPERATOR, NOW)).isInstanceOf(ValidationException.class);
    }
}

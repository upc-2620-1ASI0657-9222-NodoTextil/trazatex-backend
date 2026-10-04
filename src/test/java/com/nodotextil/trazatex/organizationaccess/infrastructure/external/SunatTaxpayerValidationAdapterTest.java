package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SunatTaxpayerValidationAdapterTest {

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://sunat.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final SunatTaxpayerValidationAdapter adapter = new SunatTaxpayerValidationAdapter(
			builder.build(), "secret-token");

	@Test
	void anActiveHabidoTaxpayerIsValid() {
		server.expect(requestTo("https://sunat.test/v1/contribuyentes/20123456789"))
				.andExpect(header("Authorization", "Bearer secret-token"))
				.andRespond(withSuccess("{\"estado\":\"ACTIVO\",\"condicion\":\"HABIDO\","
						+ "\"razonSocial\":\"X\"}", MediaType.APPLICATION_JSON));

		assertThat(adapter.isActiveAndHabido("20123456789")).isTrue();
		server.verify();
	}

	@Test
	void aTaxpayerThatIsNotActiveOrNotHabidoIsInvalid() {
		server.expect(requestTo("https://sunat.test/v1/contribuyentes/20123456789"))
				.andRespond(withSuccess("{\"estado\":\"BAJA\",\"condicion\":\"HABIDO\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://sunat.test/v1/contribuyentes/20987654321"))
				.andRespond(withSuccess("{\"estado\":\"ACTIVO\",\"condicion\":\"NO HABIDO\"}",
						MediaType.APPLICATION_JSON));

		assertThat(adapter.isActiveAndHabido("20123456789")).isFalse();
		assertThat(adapter.isActiveAndHabido("20987654321")).isFalse();
	}

	@Test
	void anUnknownRucIsInvalid() {
		server.expect(requestTo("https://sunat.test/v1/contribuyentes/20123456789"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(adapter.isActiveAndHabido("20123456789")).isFalse();
	}

	@Test
	void aMalformedRucIsInvalidWithoutCallingSunat() {
		assertThat(adapter.isActiveAndHabido("123")).isFalse();
		assertThat(adapter.isActiveAndHabido(null)).isFalse();
		server.verify();
	}

	@Test
	void anOutageIsReportedAsUnavailable() {
		server.expect(requestTo("https://sunat.test/v1/contribuyentes/20123456789"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.isActiveAndHabido("20123456789"))
				.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void aRejectedTokenIsReportedAsUnavailableNotAsAnInvalidRuc() {
		server.expect(requestTo("https://sunat.test/v1/contribuyentes/20123456789"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> adapter.isActiveAndHabido("20123456789"))
				.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void requiresConfigurationWhenTheRealAdapterIsEnabled() {
		assertThatThrownBy(() -> new SunatTaxpayerValidationAdapter("", ""))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void devAdapterAcceptsWellFormedRucsOnly() {
		DevTaxpayerValidationAdapter dev = new DevTaxpayerValidationAdapter();

		assertThat(dev.isActiveAndHabido("20123456789")).isTrue();
		assertThat(dev.isActiveAndHabido("123")).isFalse();
		assertThat(dev.isActiveAndHabido(null)).isFalse();
	}
}

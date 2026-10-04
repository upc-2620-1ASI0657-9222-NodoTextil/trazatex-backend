package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HibpCompromisedPasswordAdapterTest {

	// SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
	private static final String PREFIX = "5BAA6";
	private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://hibp.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final HibpCompromisedPasswordAdapter adapter = new HibpCompromisedPasswordAdapter(
			builder.build());

	@Test
	void onlyTheFirstFiveCharactersOfTheHashAreSent() {
		server.expect(requestTo("https://hibp.test/range/" + PREFIX))
				.andExpect(header("Add-Padding", "true"))
				.andRespond(withSuccess("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n"
						+ SUFFIX + ":3730471\r\n", MediaType.TEXT_PLAIN));

		assertThat(adapter.isCompromised("password")).isTrue();
		server.verify();
	}

	@Test
	void aPasswordWhoseSuffixIsNotListedIsNotCompromised() {
		server.expect(requestTo("https://hibp.test/range/" + PREFIX))
				.andRespond(withSuccess("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n"
						+ "011053FD0102E94D6AE2F8B83D76FAF94F6:1\r\n", MediaType.TEXT_PLAIN));

		assertThat(adapter.isCompromised("password")).isFalse();
	}

	@Test
	void paddingEntriesWithAZeroCountAreNotBreaches() {
		server.expect(requestTo("https://hibp.test/range/" + PREFIX))
				.andRespond(withSuccess(SUFFIX + ":0\r\n", MediaType.TEXT_PLAIN));

		assertThat(adapter.isCompromised("password")).isFalse();
	}

	@Test
	void anOutageIsReportedAsUnavailable() {
		server.expect(requestTo("https://hibp.test/range/" + PREFIX))
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.isCompromised("password"))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.satisfies(error -> assertThat(error.getMessage()).doesNotContain("password"));
	}

	@Test
	void devAdapterNeverFlagsAPassword() {
		assertThat(new DevCompromisedPasswordAdapter().isCompromised("password")).isFalse();
	}
}

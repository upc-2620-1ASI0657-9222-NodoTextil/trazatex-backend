package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.TaxpayerValidationPort;
import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;
import java.time.Duration;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;


@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "true")
class SunatTaxpayerValidationAdapter implements TaxpayerValidationPort {

	private final RestClient client;
	private final String apiToken;
	private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

	@Autowired
	SunatTaxpayerValidationAdapter(@Value("${app.sunat.base-url:}") String baseUrl,
			@Value("${app.sunat.api-token:}") String apiToken) {
		this(buildClient(baseUrl, apiToken), apiToken);
	}

	SunatTaxpayerValidationAdapter(RestClient client, String apiToken) {
		this.client = client;
		this.apiToken = apiToken;
	}

	private static RestClient buildClient(String baseUrl, String apiToken) {
		if (baseUrl.isBlank() || apiToken.isBlank()) {
			throw new IllegalStateException(
					"app.sunat.base-url and app.sunat.api-token are required when "
							+ "app.external-services.enabled=true");
		}
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(3));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	@Override
	@CircuitBreaker(name = "sunat", fallbackMethod = "fallback")
	public boolean isActiveAndHabido(String ruc) {
		if (ruc == null || !ruc.matches("\\d{11}")) {
			return false;
		}
		try {
			Map<String, Object> taxpayer = client.get()
					.uri("/v1/contribuyentes/{ruc}", ruc)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() { });
			boolean valid = taxpayer != null && "ACTIVO".equals(upper(taxpayer.get("estado")))
					&& "HABIDO".equals(upper(taxpayer.get("condicion")));
			cache.put(ruc, valid);
			return valid;
		}
		catch (HttpClientErrorException.NotFound unknownRuc) {
			return false;
		}
		catch (RestClientException failure) {
			throw new ExternalServiceUnavailableException("SUNAT validation is unavailable",
					failure);
		}
	}

	private boolean fallback(String ruc, Throwable failure) {
		Boolean cached = cache.get(ruc);
		if (cached != null) {
			return cached;
		}
		throw new ExternalServiceUnavailableException("SUNAT validation is unavailable", failure);
	}

	private static String upper(Object value) {
		return value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
	}
}

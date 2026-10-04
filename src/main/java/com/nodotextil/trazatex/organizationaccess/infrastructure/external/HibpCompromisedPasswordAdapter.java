package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.CompromisedPasswordPort;
import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Checks passwords against Have I Been Pwned with k-anonymity: only the first 5 characters of
 * the password's SHA-1 are sent, never the password or its full hash. Only this module may use
 * it; it is package-private on purpose. Active only when
 * {@code app.external-services.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "true")
class HibpCompromisedPasswordAdapter implements CompromisedPasswordPort {

	private static final int PREFIX_LENGTH = 5;

	private final RestClient client;

	@Autowired
	HibpCompromisedPasswordAdapter(
			@Value("${app.hibp.base-url:https://api.pwnedpasswords.com}") String baseUrl) {
		this(buildClient(baseUrl));
	}

	HibpCompromisedPasswordAdapter(RestClient client) {
		this.client = client;
	}

	private static RestClient buildClient(String baseUrl) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	@Override
	public boolean isCompromised(String password) {
		String hash = sha1(password);
		String prefix = hash.substring(0, PREFIX_LENGTH);
		String suffix = hash.substring(PREFIX_LENGTH);
		try {
			String body = client.get().uri("/range/{prefix}", prefix)
					.header("Add-Padding", "true").retrieve().body(String.class);
			return body != null && body.lines().anyMatch(line -> isBreachedEntry(line, suffix));
		}
		catch (RestClientException failure) {
			throw new ExternalServiceUnavailableException("HIBP validation is unavailable",
					failure);
		}
	}

	/** A line is {@code SUFFIX:COUNT}; padding lines have a count of 0 and are not breaches. */
	private static boolean isBreachedEntry(String line, String suffix) {
		String[] parts = line.trim().split(":", 2);
		if (parts.length != 2 || !parts[0].equalsIgnoreCase(suffix)) {
			return false;
		}
		try {
			return Long.parseLong(parts[1].trim()) > 0;
		}
		catch (NumberFormatException malformedCount) {
			return false;
		}
	}

	private static String sha1(String password) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1")
					.digest(password.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().withUpperCase().formatHex(digest).toUpperCase(Locale.ROOT);
		}
		catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException("SHA-1 is not available", unavailable);
		}
	}
}

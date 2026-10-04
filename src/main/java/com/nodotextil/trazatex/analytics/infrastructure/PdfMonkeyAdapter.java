package com.nodotextil.trazatex.analytics.infrastructure;

import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import com.nodotextil.trazatex.analytics.domain.ReportTest;
import com.nodotextil.trazatex.shared.external.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "true")
public class PdfMonkeyAdapter implements PdfGeneratorPort {

    private final RestClient client;
    private final String apiKey;
    private final String templateId;

    public PdfMonkeyAdapter(
            @Value("${app.pdfmonkey.api-key}") String apiKey,
            @Value("${app.pdfmonkey.template-id}") String templateId) {
        if (apiKey.isBlank() || templateId.isBlank()) {
            throw new ExternalServiceException("PDFMonkey configuration is required");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.client = RestClient.builder()
                .baseUrl("https://api.pdfmonkey.io/api/v1")
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey;
        this.templateId = templateId;
    }

    @Override
    @CircuitBreaker(name = "pdfmonkey")
    @SuppressWarnings("unchecked")
    public GeneratedPdf generateQualityControlReport(UUID controlId, List<ReportTest> tests) {
        try {
            Map<String, Object> response = client.post()
                    .uri("/documents")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("document", Map.of(
                            "template_id", templateId,
                            "payload", Map.of(
                                    "qualityControlId", controlId.toString(),
                                    "tests", tests))))
                    .retrieve()
                    .body(Map.class);

            Object document = response == null ? null : response.get("document");
            if (!(document instanceof Map<?, ?> map) || map.get("download_url") == null) {
                throw new ExternalServiceException("PDFMonkey returned an invalid response");
            }
            return new GeneratedPdf(map.get("download_url").toString());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("PDFMonkey report generation failed", e);
        }
    }
}
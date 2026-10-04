package com.nodotextil.trazatex.quality.infrastructure.external;

import com.nodotextil.trazatex.quality.application.contract.QualityEvidenceStorage;
import com.nodotextil.trazatex.quality.domain.InvalidQualityControlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class CloudinaryQualityEvidenceAdapter implements QualityEvidenceStorage {

    private final String cloudName;
    private final RestClient cloudinaryClient;

    public CloudinaryQualityEvidenceAdapter(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {

        validateCredential("CLOUDINARY_CLOUD_NAME", cloudName);
        validateCredential("CLOUDINARY_API_KEY", apiKey);
        validateCredential("CLOUDINARY_API_SECRET", apiSecret);

        this.cloudName = cloudName;
        this.cloudinaryClient = RestClient.builder()
                .baseUrl("https://api.cloudinary.com")
                .defaultHeaders(headers -> headers.setBasicAuth(apiKey, apiSecret))
                .build();
    }

    @Override
    public StoredEvidence upload(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidQualityControlException("Evidence file is required");
        }

        ByteArrayResource file = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName == null || fileName.isBlank()
                        ? "quality-evidence"
                        : fileName;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        if (contentType != null && !contentType.isBlank()) {
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, fileHeaders));
        body.add("folder", "trazatex/quality");
        body.add("resource_type", "image");

        try {
            Map<?, ?> response = cloudinaryClient.post()
                    .uri("/v1_1/{cloudName}/image/upload", cloudName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null
                    || response.get("public_id") == null
                    || response.get("secure_url") == null) {
                throw new InvalidQualityControlException(
                        "Cloudinary did not return the uploaded image information"
                );
            }

            return new StoredEvidence(
                    response.get("public_id").toString(),
                    response.get("secure_url").toString()
            );
        } catch (RestClientException exception) {
            throw new InvalidQualityControlException(
                    "The quality evidence could not be uploaded to Cloudinary"
            );
        }
    }

    private static void validateCredential(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not configured");
        }
    }
}

package com.nodotextil.trazatex.production.infrastructure.storage;

import com.nodotextil.trazatex.production.application.port.ProductionImageStoragePort;
import com.nodotextil.trazatex.production.domain.ProductionEvidenceOwnerType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CloudinaryProductionImageStorageAdapter implements ProductionImageStoragePort {

    private final RestClient restClient;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    CloudinaryProductionImageStorageAdapter(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload")
                .requestFactory(requestFactory)
                .build();
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public StoredImage store(
            ProductionEvidenceOwnerType ownerType,
            UUID ownerId,
            byte[] content,
            String filename,
            String contentType) {
        validateConfiguration();
        try {
            long timestamp = Instant.now().getEpochSecond();
            String publicId = "trazatex/production/"
                    + ownerType.name().toLowerCase(Locale.ROOT)
                    + "/"
                    + ownerId
                    + "/"
                    + UUID.randomUUID();

            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new NamedByteArrayResource(content, filename))
                    .contentType(MediaType.parseMediaType(contentType));
            body.part("public_id", publicId);
            body.part("timestamp", Long.toString(timestamp));
            body.part("api_key", apiKey);
            body.part(
                    "signature",
                    sha1("public_id=" + publicId + "&timestamp=" + timestamp + apiSecret));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(Map.class);
            if (response == null
                    || response.get("secure_url") == null
                    || response.get("public_id") == null) {
                throw new ProductionImageStorageException(
                        "Cloudinary returned an invalid response");
            }
            return new StoredImage(
                    response.get("secure_url").toString(),
                    response.get("public_id").toString());
        } catch (ProductionImageStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProductionImageStorageException("Cloudinary upload failed", exception);
        }
    }

    private void validateConfiguration() {
        if (cloudName.isBlank()
                || apiKey.isBlank()
                || apiSecret.isBlank()) {
            throw new ProductionImageStorageException(
                    "Cloudinary configuration is required");
        }
    }

    private static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte item : digest) {
                output.append(String.format("%02x", item));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename == null || filename.isBlank() ? "evidence" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}

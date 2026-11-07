package com.nodotextil.trazatex.quality.domain.model;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

import static com.nodotextil.trazatex.quality.domain.SampleImages.jpeg;
import static com.nodotextil.trazatex.quality.domain.SampleImages.png;
import static com.nodotextil.trazatex.quality.domain.SampleImages.webp;

class ImageFormatTest {

    @Test
    void theFormatIsDecidedByTheFirstBytes() {
        assertThat(ImageFormat.detect(jpeg())).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(png())).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(webp())).contains(ImageFormat.WEBP);
        assertThat(ImageFormat.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.ofContentType("image/webp")).isEqualTo(ImageFormat.WEBP);
    }

    static Stream<byte[]> notImages() {
        return Stream.of(
                "hola, esto es un texto".getBytes(StandardCharsets.UTF_8),
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8),
                "GIF89a....".getBytes(StandardCharsets.UTF_8),
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x42, 0x4D, 0, 0, 0, 0},                                   // BMP
                new byte[]{(byte) 0xFF, (byte) 0xD8},                                   // JPEG truncado antes del tercer byte
                new byte[]{(byte) 0x89, 'P', 'N', 'G'},                                 // PNG sin la firma completa
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'},         // RIFF, pero un WAV
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'},              // WebP truncado
                new byte[0]);
    }

    @ParameterizedTest
    @MethodSource("notImages")
    void anythingElseIsNotSupported(byte[] content) {
        assertThat(ImageFormat.detect(content)).isEqualTo(Optional.empty());
    }

    @Test
    void aNullContentIsNotSupported() {
        assertThat(ImageFormat.detect(null)).isEmpty();
    }

    @Test
    void theMagicBytesMustBeAtTheStartNotAnywhere() {
        byte[] shifted = new byte[40];
        System.arraycopy(jpeg(), 0, shifted, 5, 20);

        assertThat(ImageFormat.detect(shifted)).isEmpty();
    }
}

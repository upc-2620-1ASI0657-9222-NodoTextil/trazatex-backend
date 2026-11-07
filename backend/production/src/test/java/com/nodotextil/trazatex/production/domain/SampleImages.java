package com.nodotextil.trazatex.production.domain;

import java.util.Arrays;
import java.util.Random;

/** Bytes que empiezan como un JPEG, un PNG o un WebP (lo único que mira la validación), seguidos de relleno. */
public final class SampleImages {

    private SampleImages() {
    }

    public static byte[] jpeg() {
        return withTail(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 32);
    }

    public static byte[] png() {
        return withTail(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 32);
    }

    public static byte[] webp() {
        return withTail(new byte[]{'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'E', 'B', 'P'}, 32);
    }

    /** Un JPEG de exactamente {@code size} bytes. */
    public static byte[] jpegOfSize(int size) {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] bytes = new byte[size];
        System.arraycopy(head, 0, bytes, 0, head.length);
        new Random(size).nextBytes(Arrays.copyOfRange(bytes, 0, 0));
        for (int i = head.length; i < size; i++) {
            bytes[i] = (byte) (i * 31);
        }
        return bytes;
    }

    private static byte[] withTail(byte[] head, int tail) {
        byte[] bytes = Arrays.copyOf(head, head.length + tail);
        for (int i = head.length; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7 + 3);
        }
        return bytes;
    }
}

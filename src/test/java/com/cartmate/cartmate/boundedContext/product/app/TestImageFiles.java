package com.cartmate.cartmate.boundedContext.product.app;

import org.springframework.mock.web.MockMultipartFile;

public class TestImageFiles {

    public static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", padded(0xFF, 0xD8, 0xFF));
    }

    public static MockMultipartFile png() {
        return new MockMultipartFile("file", "a.png", "image/png",
                padded(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A));
    }

    public static MockMultipartFile fake() {
        return new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not an image!".getBytes());
    }

    private static byte[] padded(int... signature) {
        byte[] bytes = new byte[Math.max(12, signature.length)];
        for (int i = 0; i < signature.length; i++) {
            bytes[i] = (byte) signature[i];
        }
        return bytes;
    }
}
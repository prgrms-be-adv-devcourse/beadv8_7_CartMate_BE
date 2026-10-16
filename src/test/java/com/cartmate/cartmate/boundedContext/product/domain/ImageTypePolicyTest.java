package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ImageTypePolicyTest {
    private final ImageTypePolicy policy = new ImageTypePolicy();

    @Test
    @DisplayName("JPEG 파일은 JPEG로 판별된다")
    void detectJpeg() {
        MockMultipartFile file = file("a.jpg", jpegBytes());

        assertThat(policy.detect(file)).isEqualTo(ImageType.JPEG);
    }

    @Test
    @DisplayName("PNG 파일은 PNG로 판별된다")
    void detectPng() {
        MockMultipartFile file = file("a.png", pngBytes());

        assertThat(policy.detect(file)).isEqualTo(ImageType.PNG);
    }

    @Test
    @DisplayName("WEBP 파일은 WEBP로 판별된다")
    void detectWebp() {
        MockMultipartFile file = file("a.webp", webpBytes());

        assertThat(policy.detect(file)).isEqualTo(ImageType.WEBP);
    }

    @Test
    @DisplayName("확장자만 jpg인 텍스트 파일은 거절된다")
    void rejectFakeImage() {
        MockMultipartFile file = file("fake.jpg", "hello world!!".getBytes());

        assertThatThrownBy(() -> policy.detect(file))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("빈 파일은 거절된다")
    void rejectEmptyFile() {
        MockMultipartFile file = file("empty.jpg", new byte[0]);

        assertThatThrownBy(() -> policy.detect(file))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("헤더보다 짧은 파일은 거절된다")
    void rejectTooShortFile() {
        MockMultipartFile file = file("short.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8});

        assertThatThrownBy(() -> policy.detect(file))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("null 파일은 거절된다")
    void rejectNullFile() {
        assertThatThrownBy(() -> policy.detect(null))
                .isInstanceOf(ServiceException.class);
    }


    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/jpeg", content);
    }

    static byte[] jpegBytes() {
        return padded(0xFF, 0xD8, 0xFF);
    }

    static byte[] pngBytes() {
        return padded(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    static byte[] webpBytes() {
        return padded('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
    }

    private static byte[] padded(int... signature) {
        byte[] bytes = new byte[Math.max(12, signature.length)];
        for (int i = 0; i < signature.length; i++) {
            bytes[i] = (byte) signature[i];
        }
        return bytes;
    }
}

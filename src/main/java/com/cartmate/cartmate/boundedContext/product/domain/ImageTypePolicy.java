package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.code.ErrorCode;
import com.cartmate.cartmate.global.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;



@Service
@RequiredArgsConstructor
public class ImageTypePolicy {
    private static final int HEADER_SIZE = 12;

    public ImageType detect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
        }

        byte[] header = readHeader(file);

        if (isJpeg(header)) {
            return ImageType.JPEG;
        }
        if (isPng(header))  {
            return ImageType.PNG;
        }
        if (isWebp(header)) {
            return ImageType.WEBP;
        }

        throw new DomainException(ErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[HEADER_SIZE];
            int read = in.readNBytes(header, 0, HEADER_SIZE);
            if (read < HEADER_SIZE) {
                throw new DomainException(ErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
            }
            return header;
        } catch (IOException e) {
            throw new DomainException(ErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
        }
    }

    private boolean isJpeg(byte[] h) {
        return startsWith(h, 0xFF, 0xD8, 0xFF);
    }

    private boolean isPng(byte[] h) {
        return startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    private boolean isWebp(byte[] h) {
        return startsWith(h, 'R', 'I', 'F', 'F')
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    private boolean startsWith(byte[] data, int... signature) {
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}

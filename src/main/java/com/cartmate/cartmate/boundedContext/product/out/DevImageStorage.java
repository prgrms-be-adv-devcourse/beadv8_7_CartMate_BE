package com.cartmate.cartmate.boundedContext.product.out;

import com.cartmate.cartmate.global.code.ErrorCode;
import com.cartmate.cartmate.global.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
@Profile("dev")
public class DevImageStorage implements ImageStorage {

    @Value("${image.local.dir}")
    private String baseDir;

    @Value("${image.local.url}")
    private String baseUrl;

    @Override
    public void save(String key, MultipartFile file, String contentType) {
        try {
            Path path = Paths.get(baseDir, key);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DomainException(ErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(Paths.get(baseDir, key));
        } catch (IOException ignored) {
        }
    }

    @Override
    public String toUrl(String key) {
        return baseUrl + "/" + key;
    }
}

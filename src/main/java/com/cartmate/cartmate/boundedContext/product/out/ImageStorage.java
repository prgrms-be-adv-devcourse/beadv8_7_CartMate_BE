package com.cartmate.cartmate.boundedContext.product.out;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {
    void save(String key, MultipartFile file, String contentType);
    void delete(String key);
    String toUrl(String key);
}

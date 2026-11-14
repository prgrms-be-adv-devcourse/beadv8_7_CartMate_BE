package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
@Profile("dev")
public class DevImageStorage implements ImageStorage {

    @Value("${image.local.dir:./uploads}")
    private String baseDir;

    @Value("${image.local.url:/images}")
    private String baseUrl;

    @Override
    public void save(String key, MultipartFile file, String contentType) {
        try {
            Path path = Paths.get(baseDir, key);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED);
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

    // 로컬 폴더(baseDir/prefix)를 훑어 S3와 같게 키 순서로, startAfter 다음부터 돌려준다. 올린 시각은 파일 수정 시각
    @Override
    public List<StoredFile> list(String prefix, String startAfter, int limit) {
        Path root = Paths.get(baseDir);
        Path dir = root.resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> new StoredFile(toKey(root, path), lastModified(path)))
                    .filter(file -> startAfter == null || file.key().compareTo(startAfter) > 0)
                    .sorted(Comparator.comparing(StoredFile::key))
                    .limit(limit)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 운영체제와 상관없이 S3 키와 같은 모양(posts/1/a.jpg)으로
    private static String toKey(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

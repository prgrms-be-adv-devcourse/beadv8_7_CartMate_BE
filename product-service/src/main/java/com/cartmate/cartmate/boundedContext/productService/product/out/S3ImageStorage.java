package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;

    @Value("${storage.s3.bucket}")
    private String bucket;

    @Value("${storage.s3.public-url}")
    private String publicUrl;

    @Override
    public void save(String key, MultipartFile file, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException | SdkException e) {
            log.error("S3 업로드 실패 key={}", key, e);
            throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }

    @Override
    public String toUrl(String key) {
        return publicUrl + "/" + key;
    }

    // S3는 키를 사전 순으로 돌려주고, startAfter 다음 키부터 읽을 수 있다(s3:ListBucket 권한 필요)
    @Override
    public List<StoredFile> list(String prefix, String startAfter, int limit) {
        ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(limit);
        if (startAfter != null) {
            request.startAfter(startAfter);
        }

        return s3Client.listObjectsV2(request.build()).contents().stream()
                .map(object -> new StoredFile(object.key(), object.lastModified()))
                .toList();
    }
}

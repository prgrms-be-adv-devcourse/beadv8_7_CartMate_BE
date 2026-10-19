package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.ImageType;
import com.cartmate.cartmate.boundedContext.product.domain.ImageTypePolicy;
import com.cartmate.cartmate.boundedContext.product.dto.ImageUploadTask;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class ProductImageFileUploadUseCase {

    private final ImageTypePolicy typeDetectorPolicy;
    private final ProductSupport productSupport;
    private final ImageStorage imageStorage;
    private final Executor imageUploadExecutor;

    public ProductImageFileUploadUseCase(ImageTypePolicy typeDetectorPolicy,
                                         ProductSupport productSupport,
                                         ImageStorage imageStorage,
                                         @Qualifier("imageUploadExecutor") Executor imageUploadExecutor) {
        this.typeDetectorPolicy = typeDetectorPolicy;
        this.productSupport = productSupport;
        this.imageStorage = imageStorage;
        this.imageUploadExecutor = imageUploadExecutor;
    }

    public List<UploadedImage> uploadAll(CreateProductRequest request) {
        List<ImageUploadTask> tasks = productSupport.toTasks(request.getProductList());

        // 동시에 업로드 시작
        List<String> uploadedKeys = new CopyOnWriteArrayList<>();
        List<CompletableFuture<UploadedImage>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> upload(request.getPostId(), task, uploadedKeys), imageUploadExecutor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .orTimeout(30, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            uploadedKeys.forEach(imageStorage::delete);
            throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED);
        }

        return futures.stream().map(CompletableFuture::join).toList();
    }

    private UploadedImage upload(Long postId, ImageUploadTask task, List<String> uploadedKeys) {
        ImageType type = typeDetectorPolicy.detect(task.getFile());
        String key = "posts/%d/%s.%s".formatted(postId, UUID.randomUUID(), type.getExtension());

        imageStorage.save(key, task.getFile(), type.getMimeType());
        uploadedKeys.add(key);

        return new UploadedImage(task.getProductIndex(), task.getImageSeq(), key);
    }
}

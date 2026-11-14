package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.out.ImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ImageStorage.StoredFile;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 고아 사진 파일 정리: 저장소에는 있는데 DB(사진 행)에는 키가 없는 파일을 지운다.
 *
 * <p>등록 · 수정 보상이나 복구에서 파일 삭제가 실패하면 로그만 남기고 넘어가서, 저장소에 쓰이지 않는 파일이 남을 수 있다.
 * <ul>
 *   <li>올린 지 uploadedBefore보다 오래된 파일만 본다. 업로드는 "파일 저장 → DB 저장" 순서라,
 *       그 사이의 정상 파일이 고아처럼 보이는 것을 막는다</li>
 *   <li>DB에 행이 있으면(소프트 삭제된 사진 포함) 고아가 아니다</li>
 * </ul>
 * 트랜잭션을 걸지 않는다: 조회는 페이지마다 짧게 끝나고, 파일 삭제는 DB 트랜잭션과 상관이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageOrphanCleanUseCase {
    static final String PREFIX = "posts/";
    static final int PAGE_SIZE = 1000;

    private final ImageStorage imageStorage;
    private final ProductImageRepository productImageRepository;

    // 고아 파일 수를 돌려준다. dryRun이면 지우지 않고 로그만 남긴다
    public int cleanUp(Instant uploadedBefore, boolean dryRun) {
        String startAfter = null;
        int orphanCount = 0;

        while (true) {
            // 1. 저장소 파일을 키 순서로 1000개씩 읽는다
            List<StoredFile> page = imageStorage.list(PREFIX, startAfter, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            startAfter = page.getLast().key();

            // 2. 올린 지 오래된 파일만 남긴다(업로드 중인 정상 파일 보호)
            List<String> oldKeys = page.stream()
                    .filter(file -> file.lastModified().isBefore(uploadedBefore))
                    .map(StoredFile::key)
                    .toList();
            if (oldKeys.isEmpty()) {
                continue;
            }

            // 3. 그중 DB에 있는 키를 한 번에 조회해, 없는 것만 지운다
            Set<String> inDb = new HashSet<>(productImageRepository.findExistingS3Keys(oldKeys));
            for (String key : oldKeys) {
                if (inDb.contains(key)) {
                    continue;
                }
                orphanCount++;
                delete(key, dryRun);
            }
        }
        return orphanCount;
    }

    // 하나가 실패해도 나머지는 계속 지운다. 실패한 파일은 다음 실행에서 다시 대상이 된다
    private void delete(String key, boolean dryRun) {
        if (dryRun) {
            log.info("[dry-run] 고아 사진 파일: {}", key);
            return;
        }
        try {
            imageStorage.delete(key);
            log.info("고아 사진 파일 삭제: {}", key);
        } catch (RuntimeException e) {
            log.warn("고아 사진 파일 삭제 실패, 다음 실행에서 다시 시도: {}", key, e);
        }
    }
}

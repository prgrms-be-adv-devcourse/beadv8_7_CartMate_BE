package com.cartmate.cartmate.boundedContext.productService.product.out;

import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

public interface ImageStorage {
    void save(String key, MultipartFile file, String contentType);
    void delete(String key);
    String toUrl(String key);

    // prefix 아래 파일을 키 순서로, startAfter 키 다음부터 최대 limit개 (startAfter가 null이면 처음부터)
    // 고아 사진 파일 정리에서 저장소 전체를 나눠 읽는 데 쓴다
    List<StoredFile> list(String prefix, String startAfter, int limit);

    // 저장소에 있는 파일 하나: 키와 올린 시각. 저장소 종류(S3 · 로컬 · 테스트)와 상관없이 같은 모양으로 돌려준다
    record StoredFile(String key, Instant lastModified) {
    }
}

package com.cartmate.cartmate.boundedContext.productService.product.out;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("test")
public class FakeImageStorage implements ImageStorage {

    // 키 → 올린 시각. S3처럼 키 순서로 읽을 수 있게 정렬된 맵을 쓴다
    private final NavigableMap<String, Instant> saved = new ConcurrentSkipListMap<>();
    private final AtomicInteger saveCount = new AtomicInteger();
    private volatile int failOnNthSave = -1;

    @Override
    public void save(String key, MultipartFile file, String contentType) {
        if (saveCount.incrementAndGet() == failOnNthSave) {
            throw new RuntimeException("테스트용 저장 실패");
        }
        saved.put(key, Instant.now());
    }

    @Override
    public void delete(String key) {
        saved.remove(key);
    }

    @Override
    public String toUrl(String key) {
        return "http://test/" + key;
    }

    @Override
    public List<StoredFile> list(String prefix, String startAfter, int limit) {
        NavigableMap<String, Instant> from = startAfter == null ? saved : saved.tailMap(startAfter, false);
        return from.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .limit(limit)
                .map(entry -> new StoredFile(entry.getKey(), entry.getValue()))
                .toList();
    }

    public void failOnNthSave(int n) { this.failOnNthSave = n; }
    public int savedCount() { return saved.size(); }
    public boolean exists(String key) { return saved.containsKey(key); }

    // 테스트용: DB와 상관없이 저장소에만 있는 파일을 둔다(고아 파일 만들기)
    public void putFile(String key, Instant lastModified) {
        saved.put(key, lastModified);
    }

    // 테스트용: 이미 있는 파일을 올린 지 오래된 것으로 만든다
    public void uploadedAgo(String key, Duration ago) {
        saved.computeIfPresent(key, (k, v) -> Instant.now().minus(ago));
    }

    public void reset() {
        saved.clear();
        saveCount.set(0);
        failOnNthSave = -1;
    }
}

package com.cartmate.cartmate.boundedContext.product.out;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("test")
public class FakeImageStorage implements ImageStorage {

    private final Set<String> saved = ConcurrentHashMap.newKeySet();
    private final AtomicInteger saveCount = new AtomicInteger();
    private volatile int failOnNthSave = -1;

    @Override
    public void save(String key, MultipartFile file, String contentType) {
        if (saveCount.incrementAndGet() == failOnNthSave) {
            throw new RuntimeException("테스트용 저장 실패");
        }
        saved.add(key);
    }

    @Override
    public void delete(String key) {
        saved.remove(key);
    }

    @Override
    public String toUrl(String key) {
        return "http://test/" + key;
    }

    public void failOnNthSave(int n) { this.failOnNthSave = n; }
    public int savedCount() { return saved.size(); }
    public boolean exists(String key) { return saved.contains(key); }

    public void reset() {
        saved.clear();
        saveCount.set(0);
        failOnNthSave = -1;
    }
}

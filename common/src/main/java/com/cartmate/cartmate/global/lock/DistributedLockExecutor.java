package com.cartmate.cartmate.global.lock;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class DistributedLockExecutor {

    private static final String KEY_PREFIX = "lock:";
    private static final long DEFAULT_WAIT_SECONDS = 3;

    private final RedissonClient redissonClient;

    public DistributedLockExecutor(@Lazy RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // 키를 실행 도중에 알게 되는 곳에서 직접 쓴다. 어노테이션을 붙일 수 없는 경우용이다.
    public void run(String key, Runnable action) {
        try {
            call(KEY_PREFIX + key, DEFAULT_WAIT_SECONDS, () -> {
                action.run();
                return null;
            });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    // run과 같지만 결과를 돌려준다. 락 키를 만들려고 먼저 조회해야 하는 곳(예: 클레임 → 배송)에서 쓴다.
    public <T> T get(String key, Supplier<T> action) {
        try {
            return call(KEY_PREFIX + key, DEFAULT_WAIT_SECONDS, action::get);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    // 락을 얻은 동안만 action을 실행한다. leaseTime을 주지 않아 watchdog이 작업 중 락을 자동 연장한다.
    public <T> T call(String key, long waitSeconds, LockedAction<T> action) throws Throwable {
        RLock lock = redissonClient.getLock(key);

        boolean acquired;
        try {
            acquired = lock.tryLock(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(LockErrorCode.LOCK_ACQUISITION_FAILED);
        }

        if (!acquired) {
            throw new ServiceException(LockErrorCode.LOCK_ACQUISITION_FAILED);
        }

        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @FunctionalInterface
    public interface LockedAction<T> {
        T get() throws Throwable;
    }
}

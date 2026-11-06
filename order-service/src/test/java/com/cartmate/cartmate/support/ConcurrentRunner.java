package com.cartmate.cartmate.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class ConcurrentRunner {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);

    private ConcurrentRunner() {
    }

    @FunctionalInterface
    public interface Task {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface IndexedTask {
        void run(int index) throws Exception;
    }

    public static Results run(Task... tasks) throws InterruptedException {
        List<Task> list = List.of(tasks);
        return run(list.size(), index -> list.get(index).run());
    }

    public static Results run(int count, IndexedTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        AtomicReferenceArray<Throwable> errors = new AtomicReferenceArray<>(count);
        boolean finished = false;
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.run(index);
                    } catch (Throwable e) {
                        errors.set(index, e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            if (!ready.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("작업 스레드 " + count + "개가 출발선에 모두 서지 못했다(남은 수: " + ready.getCount() + ")");
            }
            start.countDown();
            if (!done.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("동시 작업 " + count + "개 중 " + done.getCount() + "개가 " + TIMEOUT.toSeconds()
                        + "초 안에 끝나지 않았다(교착 · 락 대기 의심)");
            }
            finished = true;
        } finally {
            start.countDown();
            shutdown(executor, finished);
        }
        Throwable[] snapshot = new Throwable[count];
        for (int i = 0; i < count; i++) {
            snapshot[i] = errors.get(i);
        }
        return new Results(snapshot);
    }

    private static void shutdown(ExecutorService executor, boolean finished) throws InterruptedException {
        if (finished) {
            executor.shutdown();
            if (executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
        }
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    public static final class Results {

        private final Throwable[] errors;

        private Results(Throwable[] errors) {
            this.errors = errors;
        }

        public int size() {
            return errors.length;
        }

        public boolean succeeded(int index) {
            return errors[index] == null;
        }

        public Throwable error(int index) {
            return errors[index];
        }

        public long successCount() {
            return Arrays.stream(errors).filter(e -> e == null).count();
        }

        public List<Throwable> errors() {
            return Arrays.stream(errors).filter(e -> e != null).toList();
        }

        public <T extends Throwable> List<T> errorsOf(Class<T> type) {
            return errors().stream().filter(type::isInstance).map(type::cast).toList();
        }

        @SafeVarargs
        public final List<Throwable> unexpected(Class<? extends Throwable>... allowed) {
            return errors().stream()
                    .filter(e -> Arrays.stream(allowed).noneMatch(type -> type.isInstance(e)))
                    .toList();
        }

        @SafeVarargs
        public final Results assertNoUnexpected(Class<? extends Throwable>... allowed) {
            List<Throwable> unexpected = unexpected(allowed);
            if (!unexpected.isEmpty()) {
                AssertionError error = new AssertionError("동시 작업에서 예상 밖 예외 " + unexpected.size() + "건(허용: "
                        + Arrays.stream(allowed).map(Class::getSimpleName).toList() + "): " + unexpected);
                unexpected.forEach(error::addSuppressed);
                throw error;
            }
            return this;
        }

        @Override
        public String toString() {
            List<String> outcomes = new ArrayList<>();
            for (int i = 0; i < errors.length; i++) {
                outcomes.add(i + "=" + (errors[i] == null ? "OK" : errors[i].toString()));
            }
            return "Results" + outcomes;
        }
    }
}

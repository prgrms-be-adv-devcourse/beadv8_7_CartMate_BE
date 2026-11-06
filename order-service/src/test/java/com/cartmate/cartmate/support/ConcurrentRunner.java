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

/**
 * 동시성 테스트용 실행기. 작업 N개를 스레드 N개에 올려 모두 출발선(ready)에 선 뒤 한 번에(start) 출발시키고,
 * 모두 끝날 때까지 기다려 작업마다 성공 / 던진 예외를 {@link Results}로 돌려준다.
 *
 * <ul>
 *   <li>제한 시간 안에 모두 끝나지 않으면 테스트를 실패시킨다(교착 · 무한 대기를 결과로 숨기지 않음)</li>
 *   <li>작업이 던진 예외는 삼키지 않고 모은다. 테스트는 허용한 예외(예: 업무 예외 {@code ServiceException})만
 *       나왔는지 {@link Results#assertNoUnexpected(Class[])}로 단정한다</li>
 *   <li>호출마다 executor를 만들고 끝나면 {@code shutdown} 후 종료를 기다린다. 라운드를 반복하는 테스트는 라운드마다 그냥 부르면 된다</li>
 * </ul>
 */
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

    /** 서로 다른 작업들을 동시에 출발시킨다. 결과의 순서는 인자 순서와 같다. */
    public static Results run(Task... tasks) throws InterruptedException {
        List<Task> list = List.of(tasks);
        return run(list.size(), index -> list.get(index).run());
    }

    /** 같은 작업을 {@code count}개 스레드에서 동시에 출발시킨다. 작업은 0부터 시작하는 자기 순번을 받는다. */
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
        // 끝나지 않은 작업이 있으면 끊고 짧게만 기다린다(실패 원인은 이미 위에서 던졌다)
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /** 작업마다의 결과. 인덱스는 작업 순번이다. 예외가 없으면 그 작업은 성공이다. */
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

        /** 그 작업이 던진 예외. 성공했으면 null. */
        public Throwable error(int index) {
            return errors[index];
        }

        public long successCount() {
            return Arrays.stream(errors).filter(e -> e == null).count();
        }

        /** 실패한 작업들의 예외(순번 순). */
        public List<Throwable> errors() {
            return Arrays.stream(errors).filter(e -> e != null).toList();
        }

        /** 실패한 작업 가운데 {@code type} 예외만. */
        public <T extends Throwable> List<T> errorsOf(Class<T> type) {
            return errors().stream().filter(type::isInstance).map(type::cast).toList();
        }

        /** 허용한 예외 종류(하위 타입 포함)가 아닌 예외. 비어 있어야 정상이다. */
        @SafeVarargs
        public final List<Throwable> unexpected(Class<? extends Throwable>... allowed) {
            return errors().stream()
                    .filter(e -> Arrays.stream(allowed).noneMatch(type -> type.isInstance(e)))
                    .toList();
        }

        /**
         * 허용한 예외 말고는 아무 예외도 없었는지 단정한다. 인자가 없으면 모든 작업이 성공해야 한다.
         * 실패 메시지에 예상 밖 예외의 스택을 붙인다(suppressed).
         */
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

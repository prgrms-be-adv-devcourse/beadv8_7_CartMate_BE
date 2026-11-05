package com.cartmate.cartmate.boundedContext.cart.app.personal;

import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class CartAddItemConcurrencyTest {

    // 커넥션 풀(기본 10)보다 많이 띄워 요청당 커넥션 하나로 끝나는지도 본다
    private static final int THREADS = 16;

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        memberIds.forEach(memberId -> {
            jdbcTemplate.update("DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id = ?)", memberId);
            jdbcTemplate.update("DELETE FROM cart WHERE member_id = ?", memberId);
        });
    }

    @Test
    @DisplayName("장바구니가 없는 회원이 같은 상품을 동시에 여러 번 담아도 예외 없이 장바구니 1개 · 항목 1개에 수량이 모두 합쳐진다")
    void concurrentFirstAddOfSameItem() throws Exception {
        Long memberId = newMemberId();

        List<Throwable> errors = runConcurrently(THREADS, i -> cartFacade.addItem(memberId, 1L, 1));

        assertThat(errors).isEmpty();
        assertThat(cartCount(memberId)).isEqualTo(1);
        List<CartItem> items = itemsOf(memberId);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getQuantity()).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("장바구니가 없는 회원이 서로 다른 상품을 동시에 담아도 장바구니 1개에 모두 담긴다")
    void concurrentFirstAddOfDifferentItems() throws Exception {
        Long memberId = newMemberId();
        long[] inventoryIds = {1L, 2L, 3L, 4L};

        List<Throwable> errors = runConcurrently(inventoryIds.length,
                i -> cartFacade.addItem(memberId, inventoryIds[i], 1));

        assertThat(errors).isEmpty();
        assertThat(cartCount(memberId)).isEqualTo(1);
        assertThat(itemsOf(memberId)).extracting(CartItem::getInventoryId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    private Long newMemberId() {
        Long memberId = 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        memberIds.add(memberId);
        return memberId;
    }

    private Integer cartCount(Long memberId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart WHERE member_id = ?", Integer.class, memberId);
    }

    private List<CartItem> itemsOf(Long memberId) {
        return transactionTemplate.execute(status -> cartRepository.findWithItemsByMemberId(memberId)
                .map(Cart::getItems)
                .map(List::copyOf)
                .orElseThrow());
    }

    private List<Throwable> runConcurrently(int count, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            futures.add(executor.submit((Callable<Void>) () -> {
                start.await();
                task.run(index);
                return null;
            }));
        }
        start.countDown();
        List<Throwable> errors = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        executor.shutdownNow();
        return errors;
    }

    @FunctionalInterface
    private interface IndexedTask {
        void run(int index);
    }
}

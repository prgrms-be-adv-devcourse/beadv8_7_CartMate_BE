package com.cartmate.cartmate.boundedContext.orderService.cart.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.CartItem;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.personal.CartRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.exception.ServiceException;
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

    @Test
    @DisplayName("같은 항목에 담기 · 수량 변경과 삭제가 동시에 와도 500(StaleState) 없이 끝나고 항목은 많아야 1개다 (ARCH-10#1)")
    void addAndRemoveSameItemConcurrently() throws Exception {
        for (int round = 0; round < 5; round++) {
            Long memberId = newMemberId();
            cartFacade.addItem(memberId, 1L, 1);
            Long cartItemId = itemsOf(memberId).getFirst().getId();

            List<Throwable> errors = runConcurrently(THREADS, i -> {
                if (i == 0) {
                    cartFacade.removeItem(memberId, cartItemId);
                } else if (i == 1) {
                    cartFacade.changeQuantity(memberId, cartItemId, 3);
                } else {
                    cartFacade.addItem(memberId, 1L, 1);
                }
            });

            // 삭제가 먼저 끝나면 수량 변경은 "항목 없음"으로 거절될 수 있다(업무 예외). 그 밖의 예외는 없어야 한다
            assertThat(errors).allMatch(e -> e instanceof ServiceException);
            assertThat(itemsOf(memberId)).hasSizeLessThanOrEqualTo(1);
        }
    }

    private Long newMemberId() {
        return 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
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

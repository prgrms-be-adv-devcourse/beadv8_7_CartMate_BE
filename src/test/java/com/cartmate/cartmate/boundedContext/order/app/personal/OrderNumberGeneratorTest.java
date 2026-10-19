package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderNumberGeneratorTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 19);
    private static final String FORMAT = "^\\d{8}-[A-HJ-KM-NP-Z2-9]{8}$";

    private final OrderNumberGenerator generator = new OrderNumberGenerator();

    @Test
    @DisplayName("주문 번호는 주문일자-8자리 랜덤 문자 형식이다")
    void generateFormat() {
        String orderNumber = generator.generateUnique(ORDER_DATE, number -> false);

        assertThat(orderNumber).matches(FORMAT);
        assertThat(orderNumber).startsWith("20261019-");
        assertThat(orderNumber).hasSize(17);
    }

    @Test
    @DisplayName("헷갈리는 문자(0, O, 1, I, L)는 사용하지 않는다")
    void generateWithoutAmbiguousCharacters() {
        for (int i = 0; i < 1000; i++) {
            String randomPart = generator.generateUnique(ORDER_DATE, number -> false).substring(9);

            assertThat(randomPart).matches("[A-HJ-KM-NP-Z2-9]{8}");
            assertThat(randomPart).doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    @DisplayName("이미 사용 중인 번호면 다시 생성해 사용 가능한 번호를 반환한다")
    void retryWhenTaken() {
        AtomicInteger calls = new AtomicInteger();

        String orderNumber = generator.generateUnique(ORDER_DATE, number -> calls.incrementAndGet() < 3);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(orderNumber).matches(FORMAT);
    }

    @Test
    @DisplayName("재시도해도 모두 사용 중이면 예외가 발생한다")
    void failWhenAlwaysTaken() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> generator.generateUnique(ORDER_DATE, number -> {
            calls.incrementAndGet();
            return true;
        }))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NUMBER_GENERATION_FAILED.getMsg());
        assertThat(calls.get()).isEqualTo(5);
    }
}

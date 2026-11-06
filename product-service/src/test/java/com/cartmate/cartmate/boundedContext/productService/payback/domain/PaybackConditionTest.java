package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.boundedContext.productService.code.PaybackErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaybackConditionTest {

    @Test
    @DisplayName("어드민 조건은 부담 주체 ADMIN, 판매자 없음, 겹침 모드가 없으면 BOTH로 만든다")
    void adminDefaultsOverlapPolicy() {
        PaybackCondition condition = PaybackCondition.admin(1L, 10, 500L, null);

        assertThat(condition.getFunder()).isEqualTo(PaybackFunder.ADMIN);
        assertThat(condition.getSellerId()).isNull();
        assertThat(condition.getOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.BOTH);
    }

    @Test
    @DisplayName("판매자 조건은 판매자 id를 갖고 겹침 모드를 갖지 않는다")
    void sellerHasNoOverlapPolicy() {
        PaybackCondition condition = PaybackCondition.seller(1L, 7L, 5, 300L);

        assertThat(condition.getFunder()).isEqualTo(PaybackFunder.SELLER);
        assertThat(condition.getSellerId()).isEqualTo(7L);
        assertThat(condition.getOverlapPolicy()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"1, 500", "100001, 500", "10, 0", "10, 100001"})
    @DisplayName("기준 수량 2~100,000, 개당 금액 1~100,000원을 벗어나면 거절한다")
    void rejectsOutOfRange(int minQuantity, long amountPerUnit) {
        assertThatThrownBy(() -> PaybackCondition.admin(1L, minQuantity, amountPerUnit, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(PaybackErrorCode.PAYBACK_INVALID_CONDITION.getMsg());
        assertThatThrownBy(() -> PaybackCondition.seller(1L, 7L, minQuantity, amountPerUnit))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("어드민 조건을 바꾸면 값과 겹침 모드가 바뀌고 스냅샷에 그대로 담긴다")
    void changeAdminAndSnapshot() {
        PaybackCondition condition = PaybackCondition.admin(1L, 10, 500L, PaybackOverlapPolicy.LARGER);

        condition.changeAdmin(20, 700L, PaybackOverlapPolicy.ADMIN_ONLY);
        PaybackConditionSnapshot snapshot = condition.toSnapshot();

        assertThat(snapshot.getProductId()).isEqualTo(1L);
        assertThat(snapshot.getFunder()).isEqualTo(PaybackFunder.ADMIN);
        assertThat(snapshot.getMinQuantity()).isEqualTo(20);
        assertThat(snapshot.getAmountPerUnit()).isEqualTo(700L);
        assertThat(snapshot.getOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.ADMIN_ONLY);
    }
}

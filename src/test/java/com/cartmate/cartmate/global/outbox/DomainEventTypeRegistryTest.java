package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventTypeRegistryTest {

    @Test
    @DisplayName("shared 아래의 DomainEvent 구현 7개를 단순 이름으로 모두 찾는다")
    void scansAllSharedDomainEvents() {
        DomainEventTypeRegistry registry = new DomainEventTypeRegistry();

        assertThat(registry.types()).containsEntry("OrderPaidEvent", OrderPaidEvent.class)
                .containsEntry("OrderCanceledEvent", OrderCanceledEvent.class)
                .containsEntry("OrderSellerItemsCanceledEvent", OrderSellerItemsCanceledEvent.class)
                .containsEntry("SharedGroupOrderConfirmedEvent", SharedGroupOrderConfirmedEvent.class)
                .containsEntry("SharedGroupOrderCanceledEvent", SharedGroupOrderCanceledEvent.class)
                .containsEntry("SharedGroupOrderSellerItemsCanceledEvent", SharedGroupOrderSellerItemsCanceledEvent.class)
                .containsEntry("SharedGroupPurchaseClosedEvent", SharedGroupPurchaseClosedEvent.class);
        assertThat(registry.resolve("OrderPaidEvent")).isEqualTo(OrderPaidEvent.class);
    }

    @Test
    @DisplayName("모르는 이벤트 타입은 예외")
    void unknownTypeThrows() {
        DomainEventTypeRegistry registry = new DomainEventTypeRegistry();

        assertThatThrownBy(() -> registry.resolve("NoSuchEvent")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("단순 이름이 겹치는 이벤트가 있으면 시작을 막는다")
    void duplicateSimpleNameFailsFast() {
        assertThatThrownBy(() -> new DomainEventTypeRegistry("com.cartmate.cartmate.global.outbox.duplicatetest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("겹친다");
    }
}

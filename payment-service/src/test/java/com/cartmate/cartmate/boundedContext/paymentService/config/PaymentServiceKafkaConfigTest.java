package com.cartmate.cartmate.boundedContext.paymentService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceKafkaConfigTest {

    @Test
    @DisplayName("배송 서버의 구매확정·클레임 승인 이벤트를 인박스로 받도록 바인딩한다")
    void bindsDeliveryEvents() {
        assertThat(bindings())
                .contains(new InboundEventBinding(OutboxTopics.of(DeliveryConfirmedEvent.class), DeliveryConfirmedEvent.class))
                .contains(new InboundEventBinding(OutboxTopics.of(DeliveryClaimApprovedEvent.class), DeliveryClaimApprovedEvent.class));
    }

    private static List<InboundEventBinding> bindings() {
        PaymentServiceKafkaConfig config = new PaymentServiceKafkaConfig();
        return Arrays.stream(PaymentServiceKafkaConfig.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == InboundEventBinding.class && method.getParameterCount() == 0)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(method -> invoke(config, method))
                .toList();
    }

    private static InboundEventBinding invoke(PaymentServiceKafkaConfig config, Method method) {
        try {
            return (InboundEventBinding) method.invoke(config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

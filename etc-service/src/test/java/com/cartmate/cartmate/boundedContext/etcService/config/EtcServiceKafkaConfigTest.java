package com.cartmate.cartmate.boundedContext.etcService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EtcServiceKafkaConfigTest {

    @Test
    @DisplayName("결제 서버의 환불 완료·실패 이벤트를 인박스로 받도록 바인딩한다")
    void bindsClaimRefundResults() {
        assertThat(bindings())
                .contains(new InboundEventBinding(OutboxTopics.of(ClaimRefundCompletedEvent.class), ClaimRefundCompletedEvent.class))
                .contains(new InboundEventBinding(OutboxTopics.of(ClaimRefundFailedEvent.class), ClaimRefundFailedEvent.class));
    }

    private static List<InboundEventBinding> bindings() {
        EtcServiceKafkaConfig config = new EtcServiceKafkaConfig();
        return Arrays.stream(EtcServiceKafkaConfig.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == InboundEventBinding.class && method.getParameterCount() == 0)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(method -> invoke(config, method))
                .toList();
    }

    private static InboundEventBinding invoke(EtcServiceKafkaConfig config, Method method) {
        try {
            return (InboundEventBinding) method.invoke(config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

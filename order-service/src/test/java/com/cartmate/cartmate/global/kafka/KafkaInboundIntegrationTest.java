package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.personal.CartRepository;
import com.cartmate.cartmate.boundedContext.orderService.config.OrderServiceKafkaConfig;
import com.cartmate.cartmate.boundedContext.orderService.config.OrderServiceRelayConfig;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.InboxWorker;
import com.cartmate.cartmate.global.outbox.OutboxEvent;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.support.CartMateKafkaIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.cartmate.cartmate.support.KafkaTestTopics.awaitConsumed;
import static com.cartmate.cartmate.support.KafkaTestTopics.awaitRecord;
import static com.cartmate.cartmate.support.KafkaTestTopics.header;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateKafkaIntegrationTest
class KafkaInboundIntegrationTest {

    private static final String DELIVERY_CONFIRMED = OrderServiceKafkaConfig.DELIVERY_CONFIRMED_TOPIC;
    private static final String ORDER_PAID = OutboxTopics.of(OrderPaidEvent.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private InboxWorker inboxWorker;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.consumer.group-id}")
    private String inboundGroupId;

    private final long base = 9_350_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("같은 eventId 봉투가 두 번 오면 인박스에는 처음 것 한 행만 남고 구매확정 리스너도 한 번만 돈다")
    void duplicateInboundStoredAndHandledOnce() throws Exception {
        String eventId = UUID.randomUUID().toString();
        long firstOrderId = nextId();
        long secondOrderId = nextId();

        send(DELIVERY_CONFIRMED, deliveryConfirmed(eventId, firstOrderId));
        RecordMetadata second = send(DELIVERY_CONFIRMED, deliveryConfirmed(eventId, secondOrderId));
        awaitConsumed(inboundGroupId, DELIVERY_CONFIRMED, second.partition(), second.offset());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, envelope FROM inbox_event WHERE topic = ? AND event_id = ?", DELIVERY_CONFIRMED, eventId);
        assertThat(rows).hasSize(1);
        Long inboxId = ((Number) rows.getFirst().get("id")).longValue();
        inboxWorker.process(inboxId);
        inboxWorker.process(inboxId);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM inbox_event WHERE id = ?", String.class, inboxId))
                .isEqualTo("PROCESSED");
        assertThat(confirmationCount(firstOrderId)).isEqualTo(1);
        assertThat(confirmationCount(secondOrderId)).isZero();
    }

    @Test
    @DisplayName("같은 eventId의 주문 결제 재전달이 두 번 와도 processed_event는 한 행이고 장바구니 수량은 한 번만 줄어든다")
    void duplicateRelayedOrderPaidAppliedOnce() throws Exception {
        long memberId = nextId();
        long inventoryId = nextId();
        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = Cart.create(memberId);
            cart.addItem(nextId(), inventoryId, 5);
            cartRepository.save(cart);
        });
        String eventId = UUID.randomUUID().toString();
        String value = orderPaid(eventId, memberId, inventoryId, 2);

        send(ORDER_PAID, value);
        RecordMetadata second = send(ORDER_PAID, value);
        awaitConsumed(OrderServiceRelayConfig.RELAY_GROUP_ID, ORDER_PAID, second.partition(), second.offset());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE consumer = 'cart.order-paid' AND event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ci.quantity FROM cart_item ci JOIN cart c ON c.id = ci.cart_id "
                        + "WHERE c.member_id = ? AND ci.inventory_id = ?",
                Integer.class, memberId, inventoryId)).isEqualTo(3);
    }

    @Test
    @DisplayName("봉투가 깨진 JSON이면 재시도하지 않는 예외로 분류돼 <토픽>-dlt로 가고 인박스에는 남지 않는다")
    void brokenJsonGoesToDeadLetterTopic() throws Exception {
        String key = "broken-" + UUID.randomUUID();
        String broken = "{\"eventId\":\"" + key + "\",";

        kafkaTemplate.send(new ProducerRecord<>(DELIVERY_CONFIRMED, key, broken)).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead = awaitRecord(DELIVERY_CONFIRMED + "-dlt", r -> key.equals(r.key()));
        assertThat(dead.value()).isEqualTo(broken);
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(DELIVERY_CONFIRMED);
        assertThat(Class.forName(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)))
                .isAssignableTo(JacksonException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inbox_event WHERE event_id = ?",
                Integer.class, key)).isZero();
    }

    private RecordMetadata send(String topic, String value) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(value, EventEnvelope.class);
        return kafkaTemplate.send(new ProducerRecord<>(topic, envelope.aggregateId(), value))
                .get(10, TimeUnit.SECONDS)
                .getRecordMetadata();
    }

    private String deliveryConfirmed(String eventId, long orderId) {
        LocalDateTime confirmedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DeliveryConfirmedEvent event = new DeliveryConfirmedEvent(nextId(), nextId(), orderId,
                PurchaseType.INDIVIDUAL_GROUP, confirmedAt.minusDays(9), confirmedAt, List.of());
        return envelope(eventId, "DeliveryConfirmedEvent", DELIVERY_CONFIRMED, "Delivery",
                String.valueOf(event.deliveryId()), objectMapper.writeValueAsString(event));
    }

    private String orderPaid(String eventId, long memberId, long inventoryId, int quantity) {
        long orderId = nextId();
        OrderPaidEvent event = new OrderPaidEvent(orderId, "ORD-" + orderId, memberId, OrderType.PERSONAL, null,
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null,
                List.of(new OrderPaidEvent.Item(nextId(), nextId(), inventoryId, nextId(), "상품", "옵션", quantity, 1_000L)));
        return envelope(eventId, OrderPaidEvent.class.getSimpleName(), ORDER_PAID, event.aggregateType(),
                event.aggregateId(), objectMapper.writeValueAsString(event));
    }

    private String envelope(String eventId, String eventType, String topic, String aggregateType, String aggregateId,
                            String payload) {
        return objectMapper.writeValueAsString(new EventEnvelope(eventId, eventType, topic, aggregateType, aggregateId,
                LocalDateTime.now(), OutboxEvent.SCHEMA_VERSION, payload));
    }

    private int confirmationCount(long orderId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_purchase_confirmation WHERE order_id = ?",
                Integer.class, orderId);
    }

    private long nextId() {
        return base + sequence++;
    }
}

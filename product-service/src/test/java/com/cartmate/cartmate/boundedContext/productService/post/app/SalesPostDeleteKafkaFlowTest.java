package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.KafkaOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxEvent;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.post.event.SalesPostDeletedEvent;
import com.cartmate.cartmate.shared.product.ProductStatus;
import com.cartmate.cartmate.support.ProductServiceKafkaContainer;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.cartmate.cartmate.global.inbox.QProcessedEvent.processedEvent;
import static com.cartmate.cartmate.global.outbox.QOutboxEvent.outboxEvent;
import static com.cartmate.cartmate.support.KafkaTestTopics.awaitRecord;
import static com.cartmate.cartmate.support.KafkaTestTopics.awaitUntil;
import static com.cartmate.cartmate.support.KafkaTestTopics.header;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka를 켠 판매글 삭제. post와 product는 같은 앱이지만, 켜져 있으면 서로의 이벤트를 Kafka를 거쳐서만 처리한다.
 *
 * <p>릴레이는 이벤트를 같은 앱 안과 Kafka 양쪽으로 보낸다. 리스너는 {@code @relayedEventRouting.accepts}로
 * Kafka에서 다시 받은 것(ProductServiceRelayConfig의 구독)만 처리한다. Kafka 수신은 다른 스레드라 결과를 기다려서 확인한다.
 *
 * <p>Docker로 실제 Kafka 브로커를 띄운다(ProductServiceKafkaContainer).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "cartmate.scheduling.enabled=false",
        // ManualOutboxRelayConfig의 릴레이도 @Scheduled가 붙어 있어 저절로 돈다. 테스트가 직접 돌리도록 첫 실행을 미룬다(order-service와 같은 방식)
        "custom.outbox.relay.initial-delay=PT1H",
        "cartmate.kafka.enabled=true"
})
@ActiveProfiles("test")
@ImportTestcontainers(ProductServiceKafkaContainer.class)
@Import(ManualOutboxRelayConfig.class)
class SalesPostDeleteKafkaFlowTest {

    private static final int PORT = freePort();
    private static final Long SELLER_ID = 7L;

    private static final String SALES_POST_DELETED_TOPIC = OutboxTopics.of(SalesPostDeletedEvent.class);
    private static final String PRODUCTS_DELETED_BY_POST = "ProductsDeletedByPostEvent";

    @DynamicPropertySource
    static void serverAndProductUrl(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    InProcessOutboxRelaySink inProcessOutboxRelaySink;
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbcTemplate; // global의 릴레이 잠금 풀기(ManualOutboxRelayConfig)에만 넘긴다
    @Autowired
    JPAQueryFactory queryFactory;
    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    SalesPostProductRepository salesPostProductRepository;
    @Autowired
    SalesPostProductImageRepository salesPostProductImageRepository;
    @Autowired
    SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    FakeImageStorage storage;

    Long postId;

    // 판매글 + 상품 2개를 실제 등록 흐름으로 만들어 둔다 (post 복제본까지 생성됨)
    @BeforeEach
    void setUp() {
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(outboxEvent).execute();
            queryFactory.delete(processedEvent).execute();
        });
        storage.reset();

        salesPostFacade.registerSalesPost(request(), List.of(TestImageFiles.jpeg(), TestImageFiles.png()));
        postId = salesPostRepository.findAll().getFirst().getId();
    }

    @Test
    @DisplayName("Kafka를 켜면 판매글 삭제가 Kafka를 거쳐 상품 삭제 → 판매글 삭제 완료까지 이어진다")
    void deleteThroughKafka() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);

        relay(); // SalesPostDeletedEvent → Kafka → product 상품 삭제
        awaitUntil(() -> productRepository.findAll().stream().allMatch(p -> p.getStatus() == ProductStatus.DELETED),
                "Kafka로 받은 판매글 삭제로 상품이 삭제되지 않았다");

        relay(); // ProductsDeletedByPostEvent → Kafka → post 복제본 삭제 · 판매글 삭제 완료
        awaitUntil(() -> salesPostRepository.findById(postId).orElseThrow().getStatus() == SalesPostSaleStatus.DELETED,
                "Kafka로 받은 상품 삭제 완료로 판매글 삭제가 끝나지 않았다");

        assertThat(salesPostProductRepository.findAll())
                .hasSize(2)
                .extracting(SalesPostProduct::getStatus)
                .containsOnly(ProductStatus.DELETED);
        // 같은 앱 안 전달까지 처리했다면 product가 상품 삭제 완료 이벤트를 두 번 남긴다
        assertThat(outboxCount(PRODUCTS_DELETED_BY_POST)).isEqualTo(1);
    }

    @Test
    @DisplayName("판매글 삭제 이벤트는 판매글 ID를 키로, 봉투 JSON을 값으로 토픽에 실린다")
    void salesPostDeletedMessageOnTopic() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        String eventId = queryFactory.select(outboxEvent.eventId)
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq(SalesPostDeletedEvent.class.getSimpleName()))
                .fetchOne();

        relay();

        ConsumerRecord<String, String> record = awaitRecord(SALES_POST_DELETED_TOPIC,
                r -> eventId.equals(header(r, KafkaOutboxRelaySink.HEADER_EVENT_ID)));
        assertThat(record.key()).isEqualTo(String.valueOf(postId));
        assertThat(header(record, KafkaOutboxRelaySink.HEADER_EVENT_TYPE)).isEqualTo("SalesPostDeletedEvent");

        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.topic()).isEqualTo("cartmate.post.sales-post-deleted");
        assertThat(envelope.aggregateType()).isEqualTo("SalesPost");
        SalesPostDeletedEvent event = objectMapper.readValue(envelope.payload(), SalesPostDeletedEvent.class);
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.sellerId()).isEqualTo(SELLER_ID);
    }

    @Test
    @DisplayName("Kafka를 켜면 같은 앱 안 전달은 무시하고, 같은 봉투가 Kafka로 오면 처리한다")
    void inProcessIgnoredWhenKafkaEnabled() {
        EventEnvelope envelope = envelope(new SalesPostDeletedEvent(postId, SELLER_ID));

        // 같은 앱 안 전달은 그 자리에서 리스너까지 가므로 바로 확인할 수 있다
        inProcessOutboxRelaySink.send(envelope);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.ON_SALE);

        kafkaTemplate.send(SALES_POST_DELETED_TOPIC, envelope.aggregateId(), objectMapper.writeValueAsString(envelope));

        awaitUntil(() -> productRepository.findAll().stream().allMatch(p -> p.getStatus() == ProductStatus.DELETED),
                "Kafka로 보낸 판매글 삭제로 상품이 삭제되지 않았다");
    }

    private void relay() {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    private long outboxCount(String eventType) {
        return queryFactory.select(outboxEvent.count())
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq(eventType))
                .fetchOne();
    }

    private EventEnvelope envelope(SalesPostDeletedEvent event) {
        return new EventEnvelope(UUID.randomUUID().toString(), SalesPostDeletedEvent.class.getSimpleName(),
                SALES_POST_DELETED_TOPIC, event.aggregateType(), event.aggregateId(), LocalDateTime.now(),
                OutboxEvent.SCHEMA_VERSION, objectMapper.writeValueAsString(event));
    }

    // 상품 2개, 각각 사진 1장 · 옵션 1개
    private CreateSalesPostRequest request() {
        CreateSalesPostProduct iphone = new CreateSalesPostProduct(null, null, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(new CreateProductOption(1L, 100L)));
        CreateSalesPostProduct charger = new CreateSalesPostProduct(null, null, 1L, "충전기", "설명", 3000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(new CreateProductOption(2L, 200L)));

        return new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of(iphone, charger));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

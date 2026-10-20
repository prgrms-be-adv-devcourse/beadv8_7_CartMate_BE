package com.cartmate.cartmate;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.in.DeliveryScheduler;
import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.in.batch.PayoutDueReader;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 로컬 MySQL의 전용 테스트 스키마만 사용한다. 운영/개발 DB URL은 거부한다. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.batch.job.enabled=false",
        "spring.batch.jdbc.initialize-schema=always",
        "custom.payout.cron=-",
        "custom.wallet.platform-member-id=900000001",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.orm.jdbc.bind=WARN"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "WALLET_TEST_DB_URL", matches = ".+")
@Import(CommerceLifecycleIntegrationTest.BatchTimeConfig.class)
class CommerceLifecycleIntegrationTest {
    private static LocalDateTime batchCutoff;

    @Autowired WalletFacade wallet;
    @Autowired DeliveryFacade delivery;
    @Autowired DeliveryScheduler deliveryScheduler;
    @Autowired PayoutFacade payoutFacade;
    @Autowired PayoutRepository payouts;
    @Autowired MemberRepository members;
    @Autowired SellerRepository sellers;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobOperator jobOperator;
    @Autowired Job payoutJob;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("WALLET_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/cartmate_wallet_it_[0-9_]+(\\?.*)?")) {
            throw new IllegalStateException("전용 cartmate_wallet_it_ MySQL 테스트 스키마가 필요합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("WALLET_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("WALLET_TEST_DB_PASSWORD"));
    }

    // 배치 로직과 Writer는 실제 빈을 사용하고, Reader의 기준 시각만 다음 달 15일로 이동한다.
    @TestConfiguration
    static class BatchTimeConfig {
        @Bean
        @Primary
        @StepScope
        ItemReader<Long> lifecycleDueReader(PayoutRepository repository) {
            return new PayoutDueReader(repository, batchCutoff, 100);
        }
    }

    @Test
    @DisplayName("지갑 생성·홀딩·배송·자동 구매확정 이벤트·다음 달 15일 배치 지급과 중복 방지를 검증한다")
    void lifecycleFromWalletToPayout() throws Exception {
        Member buyer = newMember("buyer");
        Member sellerMember = newMember("seller");
        Seller seller = sellers.saveAndFlush(Seller.create(sellerMember, SellerType.INDIVIDUAL,
                null, null, null, "01012345678", "테스트은행", "판매자", "123456"));
        events.publishEvent(new SellerRegisteredEvent(seller.getId(), sellerMember.getId(),
                seller.getSellerType().name(), seller.getDisplayName(), seller.getBusinessNumber(),
                seller.getPhoneNumber(), seller.getBankName(), seller.getAccountHolder(), seller.getAccountNumber(),
                seller.getStatus().name(), seller.getCreateDate(), seller.getModifyDate()));

        long buyerWallet = wallet.getWallet(buyer.getId()).walletId();
        long sellerWallet = wallet.getWallet(sellerMember.getId()).walletId();
        assertThat(balance(buyerWallet)).isZero();
        var charge = wallet.chargeOrder(buyerWallet, 20_000L, UUID.randomUUID().toString());
        wallet.charge(buyerWallet, charge.getOrderNumber(), "test-payment-" + charge.getId(), 20_000L);

        long orderId = 810001L;
        long paymentId = 820001L;
        wallet.use(buyerWallet, 10_000L, paymentId, orderId, WalletPurchaseType.SHARED_GROUP);
        wallet.use(buyerWallet, 10_000L, paymentId, orderId, WalletPurchaseType.SHARED_GROUP);
        assertThat(balance(buyerWallet)).isEqualTo(10_000L);
        assertThat(jdbc.queryForObject("select count(*) from holding where order_id=? and status='RESERVED'", Long.class, orderId)).isEqualTo(1L);

        // Order→Delivery 이벤트 연결이 아직 없으므로 이 구간만 실제 공개 생성 기능으로 이어준다.
        String key = UUID.randomUUID().toString();
        var command = new DeliveryCreateCommand(buyer.getId(), seller.getId(), orderId,
                PurchaseType.SHARED_GROUP, "수령인", "01012345678", "12345", "테스트 주소", "101호", key,
                List.of(new DeliveryCreateCommand.Item(830001L, "유니폼", 3_000L, 2L, List.of()),
                        new DeliveryCreateCommand.Item(830002L, "축구공", 4_000L, 1L, List.of())));
        delivery.createDelivery(command);
        delivery.createDelivery(command);
        long deliveryId = jdbc.queryForObject("select id from delivery where idempotency_key=?", Long.class, key);
        assertThat(jdbc.queryForObject("select count(*) from delivery_item where delivery_id=?", Long.class, deliveryId)).isEqualTo(2L);
        delivery.readyDelivery(deliveryId, sellerMember.getId(), "CJ", "6012345678");
        delivery.shipDelivery(deliveryId, sellerMember.getId());
        jdbc.update("update delivery set shipped_at=? where id=?", Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)), deliveryId);
        deliveryScheduler.completeShipped();
        assertThat(state(deliveryId)).isEqualTo("DELIVERED");
        deliveryScheduler.confirmDelivered();
        assertThat(state(deliveryId)).isEqualTo("DELIVERED"); // 7일 전에는 자동 확정하지 않는다.

        jdbc.update("update delivery set delivered_at=? where id=?", Timestamp.valueOf(LocalDateTime.now().minusDays(8)), deliveryId);
        deliveryScheduler.confirmDelivered();
        assertThat(state(deliveryId)).isEqualTo("CONFIRMED");
        List<Payout> created = payouts.findAllByOrderItemIdIn(List.of(830001L, 830002L));
        assertThat(created).hasSize(2).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(PayoutStatus.PENDING));
        LocalDateTime confirmedAt = jdbc.queryForObject("select confirmed_at from delivery where id=?", Timestamp.class, deliveryId).toLocalDateTime();
        LocalDateTime due = confirmedAt.toLocalDate().plusMonths(1).withDayOfMonth(15).atStartOfDay();
        assertThat(created).allSatisfy(p -> assertThat(p.getScheduledAt()).isEqualTo(due));
        deliveryScheduler.confirmDelivered();
        assertThat(payouts.findAllByOrderItemIdIn(List.of(830001L, 830002L))).hasSize(2);
        assertThat(balance(sellerWallet)).isZero();

        // 14일에는 Reader 대상이 없고, 15일부터 실제 지급 배치가 처리한다.
        assertThat(new PayoutDueReader(payouts, due.minusSeconds(1), 100).read()).isNull();
        batchCutoff = due;
        var execution = jobOperator.start(payoutJob, new JobParametersBuilder()
                .addString("scenario", UUID.randomUUID().toString()).toJobParameters());
        assertThat(execution.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(execution.getStepExecutions()).hasSize(1).allSatisfy(step -> {
            assertThat(step.getSkipCount()).isZero();
            assertThat(step.getWriteCount()).isEqualTo(2L);
        });
        assertThat(balance(sellerWallet)).isEqualTo(9_700L);
        long platformBalance = jdbc.queryForObject("select balance from wallet where member_id=900000001", Long.class);
        assertThat(platformBalance).isEqualTo(300L);
        assertThat(jdbc.queryForObject("select count(*) from holding where order_id=? and status='SETTLED'", Long.class, orderId)).isEqualTo(1L);
        assertThat(payouts.findAllByOrderItemIdIn(List.of(830001L, 830002L)))
                .allSatisfy(p -> {
                    assertThat(p.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
                    assertThat(p.getPaidAt()).isNotNull();
                });
        for (Payout p : created) payoutFacade.pay(p.getId());
        assertThat(balance(sellerWallet)).isEqualTo(9_700L);
        assertThat(jdbc.queryForObject("select count(*) from wallet_transaction where wallet_id=? and type='SETTLEMENT'", Long.class, sellerWallet)).isEqualTo(2L);
        assertThat(balance(buyerWallet) + balance(sellerWallet) + platformBalance).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("주문완료 이벤트만 발행해도 배송이 자동 생성되어야 한다")
    void orderCompletedEventCreatesDelivery() {
        long orderId = 890001L;
        events.publishEvent(new SharedGroupOrderConfirmedEvent(orderId, 890002L, 890003L, LocalDateTime.now(),
                new TeamDeliveryAddress("수령인", "01012345678", "12345", "주소", "101호"),
                List.of(new SharedGroupOrderConfirmedEvent.Item(890004L, 890005L, 890006L, 890007L, "상품", null, 1, 10_000L))));
        assertThat(jdbc.queryForObject("select count(*) from delivery where order_id=?", Long.class, orderId))
                .as("주문완료 이벤트를 DeliveryCreateCommand로 연결하는 리스너가 필요합니다.").isEqualTo(1L);
    }

    private Member newMember(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = members.saveAndFlush(Member.create(label + suffix + "@test.example", "encoded-test-password",
                label + suffix, "01012345678", LocalDate.of(1990, 1, 1)));
        events.publishEvent(new MemberSignedUpEvent(member.toDto()));
        return member;
    }

    private long balance(long walletId) {
        return jdbc.queryForObject("select balance from wallet where id=?", Long.class, walletId);
    }

    private String state(long deliveryId) {
        return jdbc.queryForObject("select status from delivery where id=?", String.class, deliveryId);
    }
}

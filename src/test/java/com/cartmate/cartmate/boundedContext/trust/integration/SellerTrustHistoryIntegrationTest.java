package com.cartmate.cartmate.boundedContext.trust.integration;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.in.DeliveryScheduler;
import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// 배송 생성부터 구매확정·클레임 승인까지 실제 Facade로 실행해, 커밋 뒤 리스너가 판매자 신뢰도 이력을 남기는지 확인한다.
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SellerTrustHistoryIntegrationTest {

    @Autowired
    private DeliveryFacade deliveryFacade;

    @Autowired
    private DeliveryScheduler deliveryScheduler;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private SellerTrustHistoryRepository sellerTrustHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> sellerIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        sellerIds.forEach(sellerId ->
                jdbcTemplate.update("delete from seller_trust_history where seller_id = ?", sellerId));
    }

    @Test
    @DisplayName("구매자가 구매확정하면 구매확정 이력이 저장된다")
    void buyerConfirmRecordsPurchaseConfirmed() {
        Fixture fixture = deliveredDelivery();

        deliveryFacade.confirmDelivery(fixture.deliveryId(), fixture.buyerId());

        List<SellerTrustHistory> histories = historiesOf(fixture.sellerId());
        assertThat(histories).hasSize(1);
        SellerTrustHistory history = histories.getFirst();
        assertThat(history.getType()).isEqualTo(TrustHistoryType.PURCHASE_CONFIRMED);
        assertThat(history.getSourceKey()).isEqualTo(String.valueOf(fixture.deliveryId()));
        assertThat(history.getOccurredAt()).isEqualTo(confirmedAtOf(fixture.deliveryId()));
    }

    @Test
    @DisplayName("배송 완료 후 7일이 지나 자동 구매확정되어도 구매확정 이력이 저장된다")
    void autoConfirmRecordsPurchaseConfirmed() {
        Fixture fixture = deliveredDelivery();
        jdbcTemplate.update("update delivery set delivered_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(8)), fixture.deliveryId());

        deliveryScheduler.confirmDelivered();

        assertThat(statusOf(fixture.deliveryId())).isEqualTo("CONFIRMED");
        assertThat(historiesOf(fixture.sellerId()))
                .extracting(SellerTrustHistory::getType, SellerTrustHistory::getSourceKey)
                .containsExactly(tuple(TrustHistoryType.PURCHASE_CONFIRMED, String.valueOf(fixture.deliveryId())));
    }

    @Test
    @DisplayName("이미 구매확정된 배송을 다시 확정해도 이력은 한 건만 남는다")
    void confirmTwiceRecordsOnce() {
        Fixture fixture = deliveredDelivery();

        deliveryFacade.confirmDelivery(fixture.deliveryId(), fixture.buyerId());
        deliveryFacade.confirmDelivery(fixture.deliveryId(), fixture.buyerId());

        assertThat(historiesOf(fixture.sellerId())).hasSize(1);
    }

    @Test
    @DisplayName("처리 중인 클레임이 있어 구매확정이 거부되면 이력은 남지 않는다")
    void rejectedConfirmRecordsNothing() {
        Fixture fixture = deliveredDelivery();
        createClaim(fixture, ClaimReason.SIMPLE_CHANGE);

        assertThatThrownBy(() -> deliveryFacade.confirmDelivery(fixture.deliveryId(), fixture.buyerId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_CLAIM_IN_PROGRESS");

        assertThat(statusOf(fixture.deliveryId())).isEqualTo("DELIVERED");
        assertThat(historiesOf(fixture.sellerId())).isEmpty();
    }

    @Test
    @DisplayName("판매자 귀책 사유의 클레임은 승인만으로는 이력이 남지 않고 처리 완료되면 귀책 클레임 이력이 저장된다")
    void completeFaultClaimRecordsFaultClaim() {
        Fixture fixture = deliveredDelivery();
        Long claimId = createClaim(fixture, ClaimReason.PRODUCT_DEFECT);

        deliveryFacade.approveClaim(claimId, fixture.sellerMemberId());
        assertThat(historiesOf(fixture.sellerId())).isEmpty();

        deliveryFacade.completeClaim(claimId, fixture.sellerMemberId());

        List<SellerTrustHistory> histories = historiesOf(fixture.sellerId());
        assertThat(histories).hasSize(1);
        SellerTrustHistory history = histories.getFirst();
        assertThat(history.getType()).isEqualTo(TrustHistoryType.FAULT_CLAIM);
        assertThat(history.getSourceKey()).isEqualTo(String.valueOf(claimId));
        assertThat(history.getOccurredAt()).isEqualTo(completedAtOf(claimId));
    }

    @Test
    @DisplayName("판매자 귀책이 아닌 사유의 클레임을 처리 완료해도 이력은 남지 않는다")
    void completeNonFaultClaimRecordsNothing() {
        Fixture fixture = deliveredDelivery();
        Long claimId = createClaim(fixture, ClaimReason.SIMPLE_CHANGE);

        deliveryFacade.approveClaim(claimId, fixture.sellerMemberId());
        deliveryFacade.completeClaim(claimId, fixture.sellerMemberId());

        assertThat(historiesOf(fixture.sellerId())).isEmpty();
    }

    @Test
    @DisplayName("같은 클레임의 처리 완료를 다시 요청해도 귀책 클레임 이력은 한 건만 남는다")
    void completeTwiceRecordsOnce() {
        Fixture fixture = deliveredDelivery();
        Long claimId = createClaim(fixture, ClaimReason.WRONG_DELIVERY);

        deliveryFacade.approveClaim(claimId, fixture.sellerMemberId());
        deliveryFacade.completeClaim(claimId, fixture.sellerMemberId());
        deliveryFacade.completeClaim(claimId, fixture.sellerMemberId());

        assertThat(historiesOf(fixture.sellerId())).hasSize(1);
    }

    @Test
    @DisplayName("승인된 뒤 처리 완료되지 않은 클레임은 귀책 이력에 남지 않는다")
    void approvedButNotCompletedRecordsNothing() {
        Fixture fixture = deliveredDelivery();
        Long claimId = createClaim(fixture, ClaimReason.PRODUCT_DEFECT);

        deliveryFacade.approveClaim(claimId, fixture.sellerMemberId());

        assertThat(historiesOf(fixture.sellerId())).isEmpty();
    }

    @Test
    @DisplayName("같은 유형과 식별 키의 이력을 다시 저장하면 unique 제약으로 막힌다")
    void duplicateHistoryViolatesUniqueConstraint() {
        long sellerId = newSellerId();
        String sourceKey = UUID.randomUUID().toString();
        sellerTrustHistoryRepository.save(SellerTrustHistory.create(
                sellerId, TrustHistoryType.FAULT_CLAIM, sourceKey, now()));

        assertThatThrownBy(() -> sellerTrustHistoryRepository.save(SellerTrustHistory.create(
                sellerId, TrustHistoryType.FAULT_CLAIM, sourceKey, now())))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 유형이 다르면 같은 식별 키여도 저장된다.
        sellerTrustHistoryRepository.save(SellerTrustHistory.create(
                sellerId, TrustHistoryType.PURCHASE_CONFIRMED, sourceKey, now()));
        assertThat(historiesOf(sellerId)).hasSize(2);
    }

    // 구매자·판매자·배송을 만들고 판매자 발송 처리와 배송 완료까지 진행한 상태를 준비한다.
    private Fixture deliveredDelivery() {
        Member buyer = newMember("buyer");
        Member sellerMember = newMember("seller");
        Seller seller = sellerRepository.saveAndFlush(Seller.create(sellerMember, SellerType.INDIVIDUAL,
                null, null, null, "01012345678", "테스트은행", "판매자", "123456"));
        sellerIds.add(seller.getId());

        String idempotencyKey = UUID.randomUUID().toString();
        long orderId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        deliveryFacade.createDelivery(new DeliveryCreateCommand(buyer.getId(), seller.getId(), orderId, null,
                PurchaseType.INDIVIDUAL_GROUP, "수령인", "01012345678", "12345", "테스트 주소", "101호", idempotencyKey,
                List.of(new DeliveryCreateCommand.Item(orderId + 1, "유니폼", 3_000L, 2L, List.of()))));
        long deliveryId = jdbcTemplate.queryForObject(
                "select id from delivery where idempotency_key = ?", Long.class, idempotencyKey);

        deliveryFacade.readyDelivery(deliveryId, sellerMember.getId(), "CJ", "6012345678");
        deliveryFacade.shipDelivery(deliveryId, sellerMember.getId());
        jdbcTemplate.update("update delivery set shipped_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)), deliveryId);
        deliveryScheduler.completeShipped();
        assertThat(statusOf(deliveryId)).isEqualTo("DELIVERED");

        long deliveryItemId = jdbcTemplate.queryForObject(
                "select id from delivery_item where delivery_id = ?", Long.class, deliveryId);
        return new Fixture(buyer.getId(), sellerMember.getId(), seller.getId(), deliveryId, deliveryItemId);
    }

    // 교환 클레임을 접수한다. 교환은 환불이 없어 payment가 끼어들지 않는다.
    private Long createClaim(Fixture fixture, ClaimReason reason) {
        return deliveryFacade.createClaim(new DeliveryClaimCreateCommand(
                fixture.deliveryId(), fixture.buyerId(), ClaimType.EXCHANGE, reason, "교환 요청",
                List.of(new DeliveryClaimCreateCommand.Item(fixture.deliveryItemId(), 1L)))).claimId();
    }

    private Member newMember(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.saveAndFlush(Member.create(label + suffix + "@test.example", "encoded-test-password",
                label + suffix, "01012345678", LocalDate.of(1990, 1, 1)));
    }

    private long newSellerId() {
        long sellerId = 9_400_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        sellerIds.add(sellerId);
        return sellerId;
    }

    private List<SellerTrustHistory> historiesOf(long sellerId) {
        return sellerTrustHistoryRepository.findAll().stream()
                .filter(history -> history.getSellerId().equals(sellerId))
                .toList();
    }

    private String statusOf(long deliveryId) {
        return jdbcTemplate.queryForObject("select status from delivery where id = ?", String.class, deliveryId);
    }

    private LocalDateTime confirmedAtOf(long deliveryId) {
        return jdbcTemplate.queryForObject("select confirmed_at from delivery where id = ?",
                Timestamp.class, deliveryId).toLocalDateTime();
    }

    private LocalDateTime completedAtOf(long claimId) {
        return jdbcTemplate.queryForObject("select completed_at from delivery_claim where id = ?",
                Timestamp.class, claimId).toLocalDateTime();
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private record Fixture(long buyerId, long sellerMemberId, long sellerId, long deliveryId, long deliveryItemId) {
    }
}

package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.app.InviteCodeGenerator;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.FakeProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.DemandGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.event.DomainEvent;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class IndividualGroupPurchaseDemandOpenUseCaseTest {

    private static final Long ADMIN = 1L;
    private static final Long BALL = 2L;
    private static final Long GUARD = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Mock
    private IndividualGroupPurchaseRepository repository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private IndividualGroupPurchaseDemandOpenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new IndividualGroupPurchaseDemandOpenUseCase(
                repository, new IndividualGroupPurchaseSupport(new FakeProductClient()),
                new InviteCodeGenerator(), eventPublisher);
    }

    @Test
    @DisplayName("기본 페이백 조건으로 열고 개설 이벤트를 발행한다")
    void open() {
        given(repository.findByDemandProductKeyForUpdate(BALL)).willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willAnswer(invocation -> {
            IndividualGroupPurchase purchase = invocation.getArgument(0);
            ReflectionTestUtils.setField(purchase, "id", 77L);
            return purchase;
        });

        IndividualGroupPurchaseCreateResponse response =
                useCase.open(ADMIN, BALL, new DemandGroupPurchaseOpenRequest("축구공 공동구매", null, null, null), NOW);

        assertThat(response.id()).isEqualTo(77L);
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        then(eventPublisher).should(times(2)).publish(published.capture());
        IndividualGroupPurchaseOpenedEvent opened = (IndividualGroupPurchaseOpenedEvent) published.getAllValues().get(0);
        assertThat(opened.individualGroupPurchaseId()).isEqualTo(77L);
        assertThat(opened.productIds()).containsExactly(BALL);
        assertThat(opened.revision()).isZero();
        DemandGroupPurchaseOpenedEvent event = (DemandGroupPurchaseOpenedEvent) published.getAllValues().get(1);
        assertThat(event.individualGroupPurchaseId()).isEqualTo(77L);
        assertThat(event.productId()).isEqualTo(BALL);
        assertThat(event.paybackMinBuyers()).isEqualTo(10);
        assertThat(event.paybackAmount()).isEqualTo(1_000L);
        assertThat(event.deadlineAt()).isEqualTo(NOW.plusHours(72));
        assertThat(event.inviteCode()).isEqualTo(response.inviteCode());
    }

    @Test
    @DisplayName("같은 상품으로 판매 중인 공동구매가 있으면 거절한다")
    void alreadyOpen() {
        IndividualGroupPurchase previous = IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 10, 1_000L, NOW.minusHours(1));
        given(repository.findByDemandProductKeyForUpdate(BALL)).willReturn(Optional.of(previous));

        assertThatThrownBy(() -> useCase.open(ADMIN, BALL, request(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_DEMAND_ALREADY_OPEN.getMsg());
        then(repository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("마감 시각이 지난 이전 공동구매가 키를 잡고 있으면 풀고 새로 연다")
    void releaseExpired() {
        IndividualGroupPurchase previous = IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 10, 1_000L, NOW.minusDays(4));
        given(repository.findByDemandProductKeyForUpdate(BALL)).willReturn(Optional.of(previous));
        given(repository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        useCase.open(ADMIN, BALL, request(), NOW);

        assertThat(previous.getDemandProductKey()).isNull();
        then(repository).should().flush();
    }

    @Test
    @DisplayName("동시에 열려 상품 키 유니크 제약에 걸리면 이미 열린 공동구매로 거절한다")
    void concurrentOpen() {
        given(repository.findByDemandProductKeyForUpdate(BALL)).willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '2' for key 'individual_group_purchase.uk_individual_group_purchase_demand_product'")));

        assertThatThrownBy(() -> useCase.open(ADMIN, BALL, request(), NOW))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_ALREADY_OPEN.getMsg());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("동시 개설로 잠금 대기 중 데드락이 나도 이미 열린 공동구매로 거절한다")
    void deadlockOnConcurrentOpen() {
        given(repository.findByDemandProductKeyForUpdate(BALL)).willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willThrow(new CannotAcquireLockException("deadlock"));

        assertThatThrownBy(() -> useCase.open(ADMIN, BALL, request(), NOW))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_ALREADY_OPEN.getMsg());
    }

    @Test
    @DisplayName("상품 키가 아닌 다른 제약 위반은 이미 열림으로 바꾸지 않고 그대로 던진다")
    void otherConstraintViolation() {
        given(repository.findByDemandProductKeyForUpdate(BALL)).willReturn(Optional.empty());
        DataIntegrityViolationException inviteCodeConflict = new DataIntegrityViolationException("duplicate",
                new SQLIntegrityConstraintViolationException("Duplicate entry for key 'individual_group_purchase.UK_invite_code'"));
        given(repository.saveAndFlush(any())).willThrow(inviteCodeConflict);

        assertThatThrownBy(() -> useCase.open(ADMIN, BALL, request(), NOW)).isSameAs(inviteCodeConflict);
    }

    @Test
    @DisplayName("판매할 수 없는 상품으로는 열 수 없다")
    void notPurchasable() {
        assertThatThrownBy(() -> useCase.open(ADMIN, GUARD, request(), NOW))
                .hasMessageContaining(CartErrorCode.CART_PRODUCT_NOT_ON_SALE.getMsg());
        then(repository).should(never()).saveAndFlush(any());
    }

    private static DemandGroupPurchaseOpenRequest request() {
        return new DemandGroupPurchaseOpenRequest("축구공 공동구매", null, 10, 1_000L);
    }
}

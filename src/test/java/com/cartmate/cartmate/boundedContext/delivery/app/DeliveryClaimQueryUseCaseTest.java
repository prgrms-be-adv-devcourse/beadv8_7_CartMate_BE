package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryClaimQueryUseCaseTest {

    private static final long BUYER = 1L;

    private final DeliveryClaimRepository claimRepository = mock(DeliveryClaimRepository.class);
    private final DeliveryClaimItemRepository claimItemRepository = mock(DeliveryClaimItemRepository.class);
    private final SellerQuery sellerQuery = mock(SellerQuery.class);
    private DeliveryClaimQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryClaimQueryUseCase(claimRepository, claimItemRepository, sellerQuery);
    }

    private Delivery delivery(long id) {
        Delivery delivery = Delivery.create(BUYER, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-" + id, "hash");
        ReflectionTestUtils.setField(delivery, "id", id);
        return delivery;
    }

    private DeliveryClaim claim(long id, Delivery delivery, ClaimType type) {
        DeliveryClaim claim = DeliveryClaim.create(delivery, BUYER, type, ClaimReason.SIZE_MISMATCH, "사유" + id);
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }

    private DeliveryClaimItem claimItem(DeliveryClaim claim, long itemId, long orderItemId, String name, long qty) {
        DeliveryItem deliveryItem = DeliveryItem.create(claim.getDelivery(), orderItemId, name, 10_000L, 2L);
        ReflectionTestUtils.setField(deliveryItem, "id", itemId);
        return DeliveryClaimItem.create(claim, deliveryItem, qty);
    }

    @Test
    @DisplayName("내 클레임을 상태와 상품과 함께 클레임별로 묶어 응답한다")
    void getMyClaims() {
        Pageable pageable = PageRequest.of(0, 20);
        Delivery delivery = delivery(5L);
        DeliveryClaim newer = claim(8L, delivery, ClaimType.REFUND);
        DeliveryClaim older = claim(7L, delivery, ClaimType.EXCHANGE);
        when(claimRepository.findByMemberIdOrderByCreatedAtDescIdDesc(BUYER, pageable))
                .thenReturn(new PageImpl<>(List.of(newer, older), pageable, 2));
        when(claimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(8L, 7L)))
                .thenReturn(List.of(
                        claimItem(older, 50L, 1000L, "티셔츠", 1L),
                        claimItem(newer, 51L, 1001L, "양말", 1L),
                        claimItem(older, 52L, 1002L, "모자", 2L)));

        Page<DeliveryClaimResponse> result = useCase.getMyClaims(BUYER, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        DeliveryClaimResponse first = result.getContent().get(0);
        assertThat(first.claimId()).isEqualTo(8L);
        assertThat(first.deliveryId()).isEqualTo(5L);
        assertThat(first.claimType()).isEqualTo(ClaimType.REFUND);
        assertThat(first.status()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(first.detail()).isEqualTo("사유8");
        assertThat(first.items())
                .extracting(DeliveryClaimResponse.Item::deliveryItemId, DeliveryClaimResponse.Item::productName)
                .containsExactly(tuple(51L, "양말"));

        DeliveryClaimResponse second = result.getContent().get(1);
        assertThat(second.claimId()).isEqualTo(7L);
        assertThat(second.items())
                .extracting(DeliveryClaimResponse.Item::deliveryItemId, DeliveryClaimResponse.Item::quantity)
                .containsExactly(tuple(50L, 1L), tuple(52L, 2L));
    }

    @Test
    @DisplayName("진행 상태가 바뀐 클레임은 응답에 현재 상태가 반영된다")
    void reflectsCurrentStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        DeliveryClaim claim = claim(7L, delivery(5L), ClaimType.EXCHANGE);
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.APPROVED);
        when(claimRepository.findByMemberIdOrderByCreatedAtDescIdDesc(BUYER, pageable))
                .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));
        when(claimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(7L))).thenReturn(List.of());

        assertThat(useCase.getMyClaims(BUYER, pageable).getContent().get(0).status())
                .isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    @DisplayName("클레임이 없으면 상품을 조회하지 않고 총 건수를 유지한다")
    void empty() {
        Pageable pageable = PageRequest.of(3, 20);
        when(claimRepository.findByMemberIdOrderByCreatedAtDescIdDesc(BUYER, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 45));

        Page<DeliveryClaimResponse> result = useCase.getMyClaims(BUYER, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(45);
        verify(claimItemRepository, never()).findAllWithDeliveryItemByClaimIds(any());
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 오류다")
    void memberIdRequired() {
        assertThatThrownBy(() -> useCase.getMyClaims(null, PageRequest.of(0, 20)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }

    // ---- 판매자 목록 ----

    private static final long SELLER_MEMBER = 20L;
    private static final long SELLER_ID = 10L;

    private void sellerLogin() {
        when(sellerQuery.findSellerIdByMemberId(SELLER_MEMBER)).thenReturn(Optional.of(SELLER_ID));
    }

    @Test
    @DisplayName("판매자 목록은 주문번호와 상품을 클레임별로 묶어 응답한다")
    void getSellerClaims() {
        sellerLogin();
        Pageable pageable = PageRequest.of(0, 20);
        Delivery delivery = delivery(5L);
        DeliveryClaim claim = claim(7L, delivery, ClaimType.EXCHANGE);
        when(claimRepository.findSellerClaims(eq(SELLER_ID), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));
        when(claimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(7L)))
                .thenReturn(List.of(claimItem(claim, 50L, 1000L, "티셔츠", 1L)));

        Page<SellerClaimResponse> result = useCase.getSellerClaims(SELLER_MEMBER, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        SellerClaimResponse row = result.getContent().get(0);
        assertThat(row.claimId()).isEqualTo(7L);
        assertThat(row.deliveryId()).isEqualTo(5L);
        assertThat(row.orderId()).isEqualTo(100L);
        assertThat(row.claimType()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(row.reason()).isEqualTo(ClaimReason.SIZE_MISMATCH);
        assertThat(row.detail()).isEqualTo("사유7");
        assertThat(row.status()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(row.items())
                .extracting(DeliveryClaimResponse.Item::productName, DeliveryClaimResponse.Item::quantity)
                .containsExactly(tuple("티셔츠", 1L));
    }

    @Test
    @DisplayName("상태와 유형을 주지 않으면 모든 상태와 유형으로 조회한다")
    @SuppressWarnings("unchecked")
    void sellerClaimsDefaultsToAll() {
        sellerLogin();
        Pageable pageable = PageRequest.of(0, 20);
        when(claimRepository.findSellerClaims(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        useCase.getSellerClaims(SELLER_MEMBER, null, null, pageable);
        useCase.getSellerClaims(SELLER_MEMBER, List.of(), null, pageable);

        org.mockito.ArgumentCaptor<java.util.Collection<ClaimStatus>> statuses =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        org.mockito.ArgumentCaptor<java.util.Collection<ClaimType>> types =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(claimRepository, org.mockito.Mockito.times(2))
                .findSellerClaims(eq(SELLER_ID), statuses.capture(), types.capture(), eq(pageable));
        assertThat(statuses.getAllValues()).allSatisfy(
                s -> assertThat(s).containsExactlyInAnyOrder(ClaimStatus.values()));
        assertThat(types.getAllValues()).allSatisfy(
                t -> assertThat(t).containsExactlyInAnyOrder(ClaimType.values()));
    }

    @Test
    @DisplayName("처리대기 탭처럼 여러 상태와 하나의 유형으로 걸러 조회한다")
    @SuppressWarnings("unchecked")
    void sellerClaimsWithFilters() {
        sellerLogin();
        Pageable pageable = PageRequest.of(0, 20);
        when(claimRepository.findSellerClaims(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        useCase.getSellerClaims(SELLER_MEMBER,
                List.of(ClaimStatus.REQUESTED, ClaimStatus.APPROVED), ClaimType.REFUND, pageable);

        org.mockito.ArgumentCaptor<java.util.Collection<ClaimStatus>> statuses =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        org.mockito.ArgumentCaptor<java.util.Collection<ClaimType>> types =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(claimRepository).findSellerClaims(eq(SELLER_ID), statuses.capture(), types.capture(), eq(pageable));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);
        assertThat(types.getValue()).containsExactly(ClaimType.REFUND);
    }

    @Test
    @DisplayName("판매자 목록이 비어 있으면 상품을 조회하지 않고 총 건수를 유지한다")
    void sellerClaimsEmpty() {
        sellerLogin();
        Pageable pageable = PageRequest.of(3, 20);
        when(claimRepository.findSellerClaims(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 45));

        Page<SellerClaimResponse> result = useCase.getSellerClaims(SELLER_MEMBER, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(45);
        verify(claimItemRepository, never()).findAllWithDeliveryItemByClaimIds(any());
    }

    @Test
    @DisplayName("판매자가 아니면 403 오류이고 클레임을 조회하지 않는다")
    void sellerClaimsNotSeller() {
        when(sellerQuery.findSellerIdByMemberId(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getSellerClaims(30L, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_SELLER_ONLY");
        verify(claimRepository, never()).findSellerClaims(any(), any(), any(), any());
    }

    @Test
    @DisplayName("회원 ID가 없으면 판매자 목록도 400 오류다")
    void sellerClaimsMemberIdRequired() {
        assertThatThrownBy(() -> useCase.getSellerClaims(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }
}

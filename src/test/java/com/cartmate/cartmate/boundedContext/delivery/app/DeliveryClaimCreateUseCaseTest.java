package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateCommand.Item;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryClaimCreateUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_ID = 10L;
    private static final long OTHER_MEMBER = 2L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryItemRepository itemRepository = mock(DeliveryItemRepository.class);
    private final DeliveryClaimRepository claimRepository = mock(DeliveryClaimRepository.class);
    private final DeliveryClaimItemRepository claimItemRepository = mock(DeliveryClaimItemRepository.class);
    private DeliveryClaimCreateUseCase useCase;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryClaimCreateUseCase(
                deliveryRepository, itemRepository, claimRepository, claimItemRepository);

        delivery = Delivery.create(BUYER, SELLER_ID, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));

        DeliveryItem shirt = DeliveryItem.create(delivery, 1000L, "티셔츠", 10_000L, 2L);
        DeliveryItem socks = DeliveryItem.create(delivery, 1001L, "양말", 10_000L, 1L);
        ReflectionTestUtils.setField(shirt, "id", 50L);
        ReflectionTestUtils.setField(socks, "id", 51L);
        when(itemRepository.findByDeliveryId(5L)).thenReturn(List.of(shirt, socks));
    }

    private DeliveryClaimCreateCommand command(Long memberId, ClaimType type, ClaimReason reason,
                                               String detail, List<Item> items) {
        return new DeliveryClaimCreateCommand(5L, memberId, type, reason, detail, items);
    }

    private DeliveryClaimCreateCommand valid(List<Item> items) {
        return command(BUYER, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다", items);
    }

    @Test
    @DisplayName("수령인 본인이 배송완료된 배송에서 상품을 골라 접수하면 클레임과 클레임 상품이 저장된다")
    @SuppressWarnings("unchecked")
    void create() {
        DeliveryClaimResponse response = useCase.create(valid(List.of(new Item(50L, 1L), new Item(51L, 1L))));

        ArgumentCaptor<DeliveryClaim> claimCaptor = ArgumentCaptor.forClass(DeliveryClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        DeliveryClaim saved = claimCaptor.getValue();
        assertThat(saved.getDelivery()).isSameAs(delivery);
        assertThat(saved.getMemberId()).isEqualTo(BUYER);
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.REQUESTED);

        ArgumentCaptor<List<DeliveryClaimItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(claimItemRepository).saveAll(itemCaptor.capture());
        assertThat(itemCaptor.getValue())
                .extracting(ci -> ci.getDeliveryItem().getId(), DeliveryClaimItem::getQuantity)
                .containsExactly(tuple(50L, 1L), tuple(51L, 1L));
        assertThat(itemCaptor.getValue()).allSatisfy(ci -> assertThat(ci.getClaim()).isSameAs(saved));

        assertThat(response.deliveryId()).isEqualTo(5L);
        assertThat(response.claimType()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(response.reason()).isEqualTo(ClaimReason.SIZE_MISMATCH);
        assertThat(response.status()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(response.items())
                .extracting(DeliveryClaimResponse.Item::deliveryItemId,
                        DeliveryClaimResponse.Item::productName,
                        DeliveryClaimResponse.Item::quantity)
                .containsExactly(tuple(50L, "티셔츠", 1L), tuple(51L, "양말", 1L));
    }

    @Test
    @DisplayName("배송 수량 안에서 일부 수량만 접수할 수 있고 전체 수량도 접수할 수 있다")
    void partialAndFullQuantity() {
        assertThat(useCase.create(valid(List.of(new Item(50L, 1L)))).items().get(0).quantity()).isEqualTo(1L);
        assertThat(useCase.create(valid(List.of(new Item(50L, 2L)))).items().get(0).quantity()).isEqualTo(2L);
    }

    @Test
    @DisplayName("상세 사유는 앞뒤 공백을 제거하고 비어 있으면 null로 저장한다")
    void detailIsNormalized() {
        List<Item> items = List.of(new Item(50L, 1L));

        assertThat(useCase.create(command(BUYER, ClaimType.REFUND, ClaimReason.OTHER, "  불량입니다  ", items)).detail())
                .isEqualTo("불량입니다");
        assertThat(useCase.create(command(BUYER, ClaimType.REFUND, ClaimReason.OTHER, "   ", items)).detail()).isNull();
        assertThat(useCase.create(command(BUYER, ClaimType.REFUND, ClaimReason.OTHER, null, items)).detail()).isNull();
    }

    @Test
    @DisplayName("수령인이 아닌 회원과 판매자는 접수할 수 없다")
    void onlyReceiverCanCreate() {
        List<Item> items = List.of(new Item(50L, 1L));

        assertThatThrownBy(() -> useCase.create(command(OTHER_MEMBER, ClaimType.REFUND, ClaimReason.OTHER, null, items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThatThrownBy(() -> useCase.create(command(SELLER_ID, ClaimType.REFUND, ClaimReason.OTHER, null, items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("배송완료가 아닌 상태에서는 접수할 수 없고 구매확정 이후에도 접수할 수 없다")
    void onlyDeliveredCanBeClaimed() {
        for (DeliveryStatus status : new DeliveryStatus[]{
                DeliveryStatus.REQUESTED, DeliveryStatus.PREPARING, DeliveryStatus.SHIPPING,
                DeliveryStatus.CONFIRMED, DeliveryStatus.CANCELED}) {
            ReflectionTestUtils.setField(delivery, "status", status);

            assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(50L, 1L)))))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        }
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("선택한 상품 중 하나라도 처리 중인 클레임에 들어 있으면 409 오류다")
    void duplicateActiveClaim() {
        when(claimItemRepository.existsByDeliveryItemIdsAndClaimStatuses(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(50L, 1L), new Item(51L, 1L)))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_CLAIM_ALREADY_EXISTS");
        verify(claimRepository, never()).save(any());
        verify(claimItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("같은 배송의 다른 상품은 이미 처리 중인 클레임이 있어도 따로 접수할 수 있다")
    void otherItemsCanBeClaimedSeparately() {
        // 50번 상품만 처리 중인 클레임에 있고, 이번에는 51번만 접수한다.
        when(claimItemRepository.existsByDeliveryItemIdsAndClaimStatuses(List.of(50L), activeStatuses()))
                .thenReturn(true);

        DeliveryClaimResponse response = useCase.create(valid(List.of(new Item(51L, 1L))));

        assertThat(response.items()).extracting(DeliveryClaimResponse.Item::deliveryItemId).containsExactly(51L);
    }

    @Test
    @DisplayName("접수와 승인 상태만 처리 중으로 보고, 선택한 상품 ID로 조회한다")
    @SuppressWarnings("unchecked")
    void onlyRequestedAndApprovedAreActive() {
        useCase.create(valid(List.of(new Item(50L, 1L), new Item(51L, 1L))));

        ArgumentCaptor<Collection<Long>> idCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<ClaimStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(claimItemRepository).existsByDeliveryItemIdsAndClaimStatuses(idCaptor.capture(), statusCaptor.capture());
        assertThat(idCaptor.getValue()).containsExactlyInAnyOrder(50L, 51L);
        assertThat(statusCaptor.getValue()).containsExactlyInAnyOrder(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);
    }

    @Test
    @DisplayName("이 배송의 상품이 아니면 404 오류이고 저장하지 않는다")
    void itemNotInDelivery() {
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(999L, 1L)))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_ITEM_NOT_FOUND");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("요청 수량이 배송 수량을 넘으면 400 오류다")
    void quantityExceeded() {
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(50L, 3L)))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_CLAIM_QUANTITY_EXCEEDED");
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(51L, 2L)))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_CLAIM_QUANTITY_EXCEEDED");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("입력값이 잘못되면 400 오류이고 저장하지 않는다")
    void invalidInputs() {
        List<Item> items = List.of(new Item(50L, 1L));

        assertThatThrownBy(() -> useCase.create(new DeliveryClaimCreateCommand(
                null, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null, items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_ID_REQUIRED");
        verify(deliveryRepository, never()).findById(any());
        assertThatThrownBy(() -> useCase.create(command(null, ClaimType.REFUND, ClaimReason.OTHER, null, items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
        assertThatThrownBy(() -> useCase.create(command(BUYER, null, ClaimReason.OTHER, null, items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_TYPE_REQUIRED");
        assertThatThrownBy(() -> useCase.create(command(BUYER, ClaimType.REFUND, null, null, items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_REASON_REQUIRED");
        assertThatThrownBy(() -> useCase.create(command(BUYER, ClaimType.REFUND, ClaimReason.OTHER, "가".repeat(1001), items)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_DETAIL_TOO_LONG");
        assertThatThrownBy(() -> useCase.create(valid(null)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_ITEMS_REQUIRED");
        assertThatThrownBy(() -> useCase.create(valid(List.of())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_ITEMS_REQUIRED");
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(null, 1L)))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_ITEM_ID_REQUIRED");
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(50L, null)))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_QUANTITY_INVALID");
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(50L, 0L)))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_QUANTITY_INVALID");
        assertThatThrownBy(() -> useCase.create(valid(List.of(new Item(50L, 1L), new Item(50L, 1L)))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_ITEM_DUPLICATED");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("상세 사유가 최대 길이이면 접수된다")
    void detailAtMaxLength() {
        DeliveryClaimResponse response = useCase.create(
                command(BUYER, ClaimType.REFUND, ClaimReason.OTHER, "가".repeat(1000), List.of(new Item(50L, 1L))));

        assertThat(response.detail()).hasSize(1000);
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void notFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(new DeliveryClaimCreateCommand(
                99L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null, List.of(new Item(50L, 1L)))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }

    private Collection<ClaimStatus> activeStatuses() {
        return List.of(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);
    }
}

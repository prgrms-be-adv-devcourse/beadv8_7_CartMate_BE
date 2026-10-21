package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemOptionRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryQueryUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_MEMBER = 20L;
    private static final long SELLER_ID = 10L;
    private static final long STRANGER = 30L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryItemRepository itemRepository = mock(DeliveryItemRepository.class);
    private final DeliveryItemOptionRepository optionRepository = mock(DeliveryItemOptionRepository.class);
    // isSellerOwner는 default 메서드라 실제 로직을 호출하도록 한다.
    private final SellerQuery sellerQuery = mock(SellerQuery.class, CALLS_REAL_METHODS);
    private DeliveryQueryUseCase useCase;

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryQueryUseCase(deliveryRepository, itemRepository, optionRepository, sellerQuery);

        delivery = Delivery.create(BUYER, SELLER_ID, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));

        DeliveryItem shirt = DeliveryItem.create(delivery, 1000L, "티셔츠", 10_000L, 2L);
        DeliveryItem socks = DeliveryItem.create(delivery, 1001L, "양말", 10_000L, 1L);
        ReflectionTestUtils.setField(shirt, "id", 50L);
        ReflectionTestUtils.setField(socks, "id", 51L);
        when(itemRepository.findByDeliveryId(5L)).thenReturn(List.of(shirt, socks));
        when(optionRepository.findByDeliveryItemIdInOrderByDeliveryItemIdAscSortOrderAsc(anyList()))
                .thenReturn(List.of(
                        DeliveryItemOption.create(shirt, "사이즈", "L", 0),
                        DeliveryItemOption.create(shirt, "색상", "블랙", 1)));

        doReturn(Optional.of(SELLER_ID)).when(sellerQuery).findSellerIdByMemberId(SELLER_MEMBER);
        doReturn(Optional.empty()).when(sellerQuery).findSellerIdByMemberId(STRANGER);
    }

    @Test
    @DisplayName("내 배송 목록은 조회한 페이지를 응답으로 변환한다")
    void getMyDeliveries() {
        Pageable pageable = PageRequest.of(0, 20);
        when(deliveryRepository.findByMemberIdOrderByCreatedAtDescIdDesc(BUYER, pageable))
                .thenReturn(new PageImpl<>(List.of(delivery), pageable, 1));

        Page<DeliveryResponse> result = useCase.getMyDeliveries(BUYER, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        DeliveryResponse response = result.getContent().get(0);
        assertThat(response.deliveryId()).isEqualTo(5L);
        assertThat(response.orderId()).isEqualTo(100L);
        assertThat(response.receiverName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("구매자는 상세 조회할 수 있고 상품명과 옵션이 아이템별로 묶인다")
    void detailByBuyer() {
        DeliveryDetailResponse response = useCase.getDeliveryDetails(5L, BUYER);

        assertThat(response.items()).hasSize(2);
        DeliveryDetailResponse.Item first = response.items().get(0);
        assertThat(first.deliveryItemId()).isEqualTo(50L);
        assertThat(response.items().get(1).deliveryItemId()).isEqualTo(51L);
        assertThat(first.orderItemId()).isEqualTo(1000L);
        assertThat(first.productName()).isEqualTo("티셔츠");
        assertThat(first.options())
                .extracting(DeliveryDetailResponse.Option::optionName, DeliveryDetailResponse.Option::optionValue)
                .containsExactly(tuple("사이즈", "L"), tuple("색상", "블랙"));
        assertThat(response.items().get(1).options()).isEmpty();
    }

    @Test
    @DisplayName("해당 배송의 판매자는 상세 조회할 수 있다")
    void detailBySeller() {
        assertThat(useCase.getDeliveryDetails(5L, SELLER_MEMBER).deliveryId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("구매자도 판매자도 아니면 403 권한 오류다")
    void detailByStranger() {
        assertThatThrownBy(() -> useCase.getDeliveryDetails(5L, STRANGER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        verifyNoInteractions(optionRepository);
    }

    @Test
    @DisplayName("다른 판매자의 배송은 판매자여도 403 권한 오류다")
    void detailByOtherSeller() {
        doReturn(Optional.of(99L)).when(sellerQuery).findSellerIdByMemberId(40L);

        assertThatThrownBy(() -> useCase.getDeliveryDetails(5L, 40L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void detailNotFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getDeliveryDetails(99L, BUYER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }

    @Test
    @DisplayName("판매자 목록은 상태별로 조회하고 아이템과 옵션을 배송별로 묶어 응답한다")
    void getSellerDeliveries() {
        Pageable pageable = PageRequest.of(0, 20);
        when(deliveryRepository.findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(
                SELLER_ID, DeliveryStatus.REQUESTED, pageable))
                .thenReturn(new PageImpl<>(List.of(delivery), pageable, 1));
        DeliveryItem shirt = DeliveryItem.create(delivery, 1000L, "티셔츠", 10_000L, 2L);
        ReflectionTestUtils.setField(shirt, "id", 50L);
        when(itemRepository.findByDeliveryIdIn(List.of(5L))).thenReturn(List.of(shirt));
        when(optionRepository.findByDeliveryItemIdInOrderByDeliveryItemIdAscSortOrderAsc(List.of(50L)))
                .thenReturn(List.of(DeliveryItemOption.create(shirt, "사이즈", "L", 0)));

        Page<SellerDeliveryResponse> result =
                useCase.getSellerDeliveries(SELLER_MEMBER, DeliveryStatus.REQUESTED, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        SellerDeliveryResponse row = result.getContent().get(0);
        assertThat(row.deliveryId()).isEqualTo(5L);
        assertThat(row.orderId()).isEqualTo(100L);
        assertThat(row.receiverName()).isEqualTo("홍길동");
        assertThat(row.address()).isEqualTo("서울");
        assertThat(row.items()).hasSize(1);
        assertThat(row.items().get(0).deliveryItemId()).isEqualTo(50L);
        assertThat(row.items().get(0).productName()).isEqualTo("티셔츠");
        assertThat(row.items().get(0).options())
                .extracting(DeliveryDetailResponse.Option::optionName, DeliveryDetailResponse.Option::optionValue)
                .containsExactly(tuple("사이즈", "L"));
    }

    @Test
    @DisplayName("판매자 목록이 비어 있으면 아이템을 조회하지 않고 총 건수를 유지한다")
    void getSellerDeliveriesEmpty() {
        Pageable pageable = PageRequest.of(3, 20);
        when(deliveryRepository.findBySellerIdAndStatusOrderByCreatedAtDescIdDesc(
                SELLER_ID, DeliveryStatus.SHIPPING, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 45));

        Page<SellerDeliveryResponse> result =
                useCase.getSellerDeliveries(SELLER_MEMBER, DeliveryStatus.SHIPPING, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(45);
        verifyNoInteractions(optionRepository);
    }

    @Test
    @DisplayName("판매자가 아니면 판매자 목록 조회는 403 오류다")
    void getSellerDeliveriesNotSeller() {
        assertThatThrownBy(() -> useCase.getSellerDeliveries(
                STRANGER, DeliveryStatus.REQUESTED, PageRequest.of(0, 20)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_SELLER_ONLY");
    }

    @Test
    @DisplayName("주문별 배송 목록은 구매자 본인의 해당 주문·구매 유형 배송만 응답으로 변환한다")
    void getMyDeliveriesByOrder() {
        when(deliveryRepository.findByMemberIdAndOrderIdAndPurchaseTypeOrderByCreatedAtDescIdDesc(
                BUYER, 100L, PurchaseType.INDIVIDUAL_GROUP)).thenReturn(List.of(delivery));

        List<DeliveryResponse> result = useCase.getMyDeliveriesByOrder(BUYER, 100L, PurchaseType.INDIVIDUAL_GROUP);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deliveryId()).isEqualTo(5L);
        assertThat(result.get(0).orderId()).isEqualTo(100L);
        assertThat(result.get(0).purchaseType()).isEqualTo(PurchaseType.INDIVIDUAL_GROUP);
    }

    @Test
    @DisplayName("주문별 배송이 없으면 빈 목록이다")
    void getMyDeliveriesByOrderEmpty() {
        when(deliveryRepository.findByMemberIdAndOrderIdAndPurchaseTypeOrderByCreatedAtDescIdDesc(
                STRANGER, 100L, PurchaseType.INDIVIDUAL_GROUP)).thenReturn(List.of());

        assertThat(useCase.getMyDeliveriesByOrder(STRANGER, 100L, PurchaseType.INDIVIDUAL_GROUP)).isEmpty();
    }

    @Test
    @DisplayName("주문별 배송 조회에서 회원 ID가 없으면 400 오류다")
    void getMyDeliveriesByOrderMemberIdRequired() {
        assertThatThrownBy(() -> useCase.getMyDeliveriesByOrder(null, 100L, PurchaseType.INDIVIDUAL_GROUP))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
        verifyNoInteractions(deliveryRepository);
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 오류다")
    void detailMemberIdRequired() {
        assertThatThrownBy(() -> useCase.getDeliveryDetails(5L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }
}

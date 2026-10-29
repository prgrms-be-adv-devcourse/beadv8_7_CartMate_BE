package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderMemberClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.OrderSharedGroupPurchaseClient;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SharedGroupOrderQueryUseCaseTest {

    private static final Long CREATOR = 1L;
    private static final Long MEMBER = 2L;
    private static final Long OUTSIDER = 3L;

    @Mock
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Mock
    private OrderProductClient orderProductClient;

    @Mock
    private OrderMemberClient orderMemberClient;

    @Mock
    private OrderSharedGroupPurchaseClient orderSharedGroupPurchaseClient;

    @InjectMocks
    private SharedGroupOrderQueryUseCase queryUseCase;

    @Test
    @DisplayName("참여자는 내 부담 금액과 상품 정보가 담긴 상세를 본다")
    void getDetail() {
        given(sharedGroupOrderRepository.findWithDetailsById(1L)).willReturn(Optional.of(order()));
        given(orderProductClient.findAllByInventoryIds(anyCollection()))
                .willReturn(List.of(new OrderProductInfo(4L, 2L, 1L, "축구공", "5호", 25000, "ball.png", true)));
        given(orderMemberClient.findNicknames(Set.of(CREATOR, MEMBER))).willReturn(Map.of(CREATOR, "방장", MEMBER, "철수"));

        SharedGroupOrderDetailResponse response = queryUseCase.getDetail(MEMBER, 1L);

        assertThat(response.creator()).isFalse();
        assertThat(response.myShareAmount()).isEqualTo(25000L);
        assertThat(response.participants())
                .extracting(SharedGroupOrderDetailResponse.ParticipantResponse::nickname)
                .containsExactlyInAnyOrder("방장", "철수");
        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.productName()).isEqualTo("축구공"));
        assertThat(response.teamDeliveryAddress()).isNull();
    }

    @Test
    @DisplayName("내 공동 주문 목록에 공동구매 제목, 대표 상품명, 상품 수가 담긴다")
    void getMyList() {
        given(sharedGroupOrderRepository.findAllByParticipant(MEMBER)).willReturn(List.of(order()));
        given(orderSharedGroupPurchaseClient.findTitles(Set.of(10L))).willReturn(Map.of(10L, "FC 훈련용품"));

        List<SharedGroupOrderSummaryResponse> response = queryUseCase.getMyList(MEMBER);

        assertThat(response).singleElement().satisfies(summary -> {
            assertThat(summary.purchaseTitle()).isEqualTo("FC 훈련용품");
            assertThat(summary.firstProductName()).isEqualTo("상품");
            assertThat(summary.itemCount()).isEqualTo(1);
            assertThat(summary.myShareAmount()).isEqualTo(25000L);
        });
    }

    @Test
    @DisplayName("공동구매 제목을 찾지 못해도 목록은 내려가고 제목만 비어 있다")
    void getMyListWithoutTitle() {
        given(sharedGroupOrderRepository.findAllByParticipant(MEMBER)).willReturn(List.of(order()));
        given(orderSharedGroupPurchaseClient.findTitles(Set.of(10L))).willReturn(Map.of());

        assertThat(queryUseCase.getMyList(MEMBER)).singleElement()
                .satisfies(summary -> assertThat(summary.purchaseTitle()).isNull());
    }

    @Test
    @DisplayName("참여자가 아니면 볼 수 없다")
    void notParticipant() {
        given(sharedGroupOrderRepository.findWithDetailsById(1L)).willReturn(Optional.of(order()));

        assertThatThrownBy(() -> queryUseCase.getDetail(OUTSIDER, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PARTICIPANT.getMsg());
    }

    @Test
    @DisplayName("없는 주문이면 예외가 발생한다")
    void notFound() {
        given(sharedGroupOrderRepository.findWithDetailsById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryUseCase.getDetail(MEMBER, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_FOUND.getMsg());
    }

    private SharedGroupOrder order() {
        return SharedGroupOrder.create(10L, CREATOR, List.of(CREATOR, MEMBER),
                List.of(new OrderItemLine(2L, 4L, null, "상품", null, 2, 25000)), LocalDateTime.of(2026, 10, 19, 12, 0));
    }
}

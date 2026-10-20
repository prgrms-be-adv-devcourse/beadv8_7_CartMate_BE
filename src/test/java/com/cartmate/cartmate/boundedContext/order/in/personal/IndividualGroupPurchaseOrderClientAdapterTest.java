package com.cartmate.cartmate.boundedContext.order.in.personal;

import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.order.app.personal.IndividualGroupOrderQueryFacade;
import com.cartmate.cartmate.boundedContext.order.out.personal.IndividualGroupPurchaseOrderLineView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IndividualGroupPurchaseOrderClientAdapterTest {

    private static final Long PURCHASE_ID = 10L;

    @Mock
    private IndividualGroupOrderQueryFacade individualGroupOrderQueryFacade;

    @InjectMocks
    private IndividualGroupPurchaseOrderClientAdapter adapter;

    @Test
    @DisplayName("결제 완료된 주문 줄을 현황판용 주문 줄로 그대로 옮긴다")
    void findOrderLines() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 10, 19, 12, 0);
        given(individualGroupOrderQueryFacade.findPaidIndividualGroupOrderLines(PURCHASE_ID)).willReturn(List.of(
                new IndividualGroupPurchaseOrderLineView(100L, 1L, 2L, 4L, 3, 25000L, orderedAt)));

        List<IndividualGroupPurchaseOrderLine> lines = adapter.findOrderLines(PURCHASE_ID);

        assertThat(lines).containsExactly(
                new IndividualGroupPurchaseOrderLine(100L, 1L, 2L, 4L, 3, 25000L, orderedAt));
    }
}
